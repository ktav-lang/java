# ktav — биндинги для Java

[![Maven Central](https://img.shields.io/maven-central/v/io.github.ktav-lang/ktav?style=flat-square&logo=apachemaven&logoColor=white&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.ktav-lang/ktav)
[![CI](https://img.shields.io/github/actions/workflow/status/ktav-lang/java/ci.yml?style=flat-square&logo=github&label=CI)](https://github.com/ktav-lang/java/actions)
![License: MIT OR Apache-2.0](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue?style=flat-square)
[![Playground](https://img.shields.io/badge/playground-try%20online-7c3aed?style=flat-square&logo=rocket&logoColor=white)](https://ktav-lang.github.io/)

**Языки:** [English](../../README.md) · **Русский** · [简体中文](../zh/README.zh.md)

**Песочница:** конвертация JSON / YAML / TOML / INI ⇄ Ktav прямо в браузере — **[ktav-lang.github.io](https://ktav-lang.github.io/)**.

Java-биндинги к [формату конфигурации Ktav](https://github.com/ktav-lang/spec).
Тонкая обёртка над эталонным парсером на Rust, подгружаемая в runtime
через [JNA](https://github.com/java-native-access/jna) — **никакой
сборки JNI на стороне потребителя**, обычный Gradle/Maven просто
работает.

Требуется **JDK 17+**. Пока распространяется через GitHub Releases
(публикация в Maven Central — запланирована).

## Быстрый старт

`build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    // while we're not yet on Maven Central, consume the JAR from
    // the GitHub Release — see the README for a worked example.
}

dependencies {
    implementation("io.github.ktav-lang:ktav:0.8.0")
    implementation("net.java.dev.jna:jna:5.15.0")
}
```

### Парсинг — типизированно вытаскиваем поля

```java
import lang.ktav.Ktav;
import lang.ktav.Value;

String src = """
        service: web
        port: 8080
        ratio: 0.75
        tls: true
        tags: [
            prod
            eu-west-1
        ]
        db.host: primary.internal
        db.timeout: 30
        """;

Value.Obj top = (Value.Obj) Ktav.loads(src);

String  service = ((Value.Str)  top.entries().get("service")).value();
long    port    = ((Value.Int)  top.entries().get("port")).toLong();
double  ratio   = ((Value.Flt)  top.entries().get("ratio")).toDouble();
boolean tls     = ((Value.Bool) top.entries().get("tls")).value();

Value.Obj db    = (Value.Obj) top.entries().get("db");
String dbHost   = ((Value.Str) db.entries().get("host")).value();
long   dbTimeout = ((Value.Int) db.entries().get("timeout")).toLong();
```

### Обход — диспатч по sealed-иерархии `Value`

```java
for (var e : top.entries().entrySet()) {
    if      (e.getValue() instanceof Value.Bool b) System.out.println(e.getKey() + " is bool=" + b.value());
    else if (e.getValue() instanceof Value.Int  i) System.out.println(e.getKey() + " is int=" + i.text());
    else if (e.getValue() instanceof Value.Arr  a) System.out.println(e.getKey() + " is array(" + a.items().size() + ")");
    // ...Null / Flt / Str / Obj
}
```

На JDK 21+ — `switch` expression с pattern matching по sealed type.

### Билд + рендер — собираем документ в коде

```java
import java.util.LinkedHashMap;
import java.util.List;

LinkedHashMap<String, Value> upstream = new LinkedHashMap<>();
upstream.put("host", new Value.Str("a.example"));
upstream.put("port", Value.Int.of(1080));

LinkedHashMap<String, Value> doc = new LinkedHashMap<>();
doc.put("name",      new Value.Str("frontend"));
doc.put("port",      Value.Int.of(8443));
doc.put("tls",       Value.Bool.TRUE);
doc.put("ratio",     Value.Flt.of(0.95));
doc.put("upstreams", new Value.Arr(List.of(new Value.Obj(upstream))));
doc.put("notes",     Value.Null.NULL);

String text = Ktav.dumps(new Value.Obj(doc));
// name: frontend
// port: 8443
// tls: true
// ratio: 0.95
// upstreams: [
//     {
//         host: a.example
//         port: 1080
//     }
// ]
// notes: null
```

Полный запускаемый пример — в [`examples/basic`](../../examples/basic/src/main/java/examples/Basic.java).

## API

| Функция | Назначение |
| --- | --- |
| `Ktav.loads(String) -> Value` | Разобрать Ktav-документ в дерево `Value`. |
| `Ktav.loadsStrict(String) -> Value` | Разобрать документ со строгой проверкой записи чисел. |
| `Ktav.dumps(Value) -> String` | Отрендерить `Value` обратно в Ktav-текст. Верхний уровень должен быть `Obj`. |
| `Ktav.toStringForceStrings(Value) -> String` | Отрендерить как `dumps`, но привести каждый leaf-скаляр к String. |
| `Ktav.emitCanonical(Value) -> String` | Отрендерить `Value` в детерминированной канонической форме. |
| `Ktav.format(String) -> String` | Нормализовать написание документа, сохраняя комментарии. |
| `Ktav.canonicalFromSource(String) -> String` | Разобрать и заново вывести как канонический Ktav за один вызов — `emitCanonical(loads(src))` без промежуточного `Value`. Комментарии и пустые строки отбрасывает так же, как `emitCanonical`. |
| `Ktav.nativeVersion() -> String` | Версия подгруженного `ktav_cabi`. |

`toStringForceStrings` расплющивает целые, float, булевы и `null` в их
текстовую форму через сырой маркер (`::`); объекты и массивы сохраняют
структуру, потому что приводятся только листья. Результат разбирается
обратно через `loads` как тот же набор String-скаляров — полезно, когда
потребитель на выходе не понимает типизированных маркеров.

### Форматирование

`Ktav.format()` принимает Ktav-**исходный текст** и возвращает
Ktav-исходный текст — это не рендерер `Value`. Он приводит структуру к
канонической форме (§ 5.9), сохраняя ту тривию, которую канонический
writer отбрасывает:

```java
System.out.print(Ktav.format("## the server\nserver: {host: a, port: 80}\n"));
// ## the server
// server: {
//     host: a
//     port: 80
// }
```

Каждый комментарий переживает форматирование дословно — в Ktav нет
замыкающих комментариев (§ 3.4: комментарий занимает строку целиком),
поэтому привязка однозначна. Пустые строки выживают как подсказка
группировки, но серия из двух и более схлопывается ровно в одну, а
пустая отбивка сразу внутри скобки убирается — это и делает
преобразование неподвижной точкой: форматирование уже отформатированного
текста ничего не меняет. Порядок ключей не меняется — в канонической
форме нет правила сортировки, а перестановка ключей только ухудшила бы
диффы при ревью.

Для документа без комментариев **и без пустых строк** результат равен
`Ktav.emitCanonical(Ktav.loads(src))`. Усиленное условие намеренно:
пустые строки входят в модель `Value` не больше, чем комментарии,
поэтому канонический writer их отбрасывает, а `format` — нет.

### Ошибки

`KtavException` бросается на любой ошибке разбора или рендеринга. Помимо
человекочитаемого `getMessage()` оно несёт остальные девять
структурированных полей конверта ошибки ядра через десять аксессоров —
`span` разложен на `getSpanStart()` / `getSpanEnd()`, а не упакован в
отдельный тип-пару. `getMessage()` — это и есть десятое поле конверта,
`message`, взятое дословно, а не пересобранное из остальных девяти:

```java
try {
    Ktav.loadsStrict("version: 1.10\n");
} catch (KtavException e) {
    e.getError();        // "LossyScalar"
    e.getLine();         // 1
    e.getLineText();     // "version: 1.10"
    e.getBody();         // "1.10"  — as written
    e.getCanonical();    // "1.1"   — as it would be stored
    e.getSpecSection();  // "§3.6/§5.2"
}
```

Полный набор — `getError()`, `getReason()`, `getLine()`,
`getLineText()`, `getSpanStart()`, `getSpanEnd()`, `getPath()`,
`getBody()`, `getCanonical()`, `getSpecSection()`. Отсутствующая
информация — это `null` (именно ради этого возвращаются боксированные
`Long`), а не пропавший аксессор, поэтому любое поле можно прочитать, не
проверяя предварительно класс ошибки.

`getPath()` возвращает `List<String>` **точных декодированных сегментов
ключа, а не склеенную строку**: ключ, буквально названный `a.b`, — это
один сегмент, и его нельзя спутать с двухсегментным путём.

Два отказа writer'а названы по-разному: `"UnrepresentableAt"`, когда
writer может указать виновный узел (тогда он заполняет и `getPath()`), и
`"Unrepresentable"`, когда не может. Код `reason` у них одинаковый,
поэтому сопоставления по `getReason()` достаточно, если нужно лишь
знать, что запись отвергнута.

## Маппинг типов

Повторяет enum `Value` из Rust-крейта — один вариант на каждый
примитив Ktav, без лоссных приведений:

| Ktav             | вариант `Value`                                         |
| ---------------- | ------------------------------------------------------- |
| `null`           | `Value.Null.NULL`                                       |
| `true` / `false` | `Value.Bool`                                            |
| голое целое      | `Value.Int` (текстовая форма — произвольная точность, `toBigInteger()` / `toLong()`) |
| голое десятичное | `Value.Flt` (текстовая форма — точный round-trip, `toDouble()`) |
| прочий скаляр    | `Value.Str`                                             |
| `[ ... ]`        | `Value.Arr` (`List<Value>`)                             |
| `{ ... }`        | `Value.Obj` (`LinkedHashMap<String, Value>`, порядок вставки сохранён) |

Целые и float хранятся **как текст**, чтобы произвольная
точность (цифры сверх `long`) и точное представление десятичного числа
побайтово сохранялись между циклами parse/render.

## Экранирование в ключах

Начиная со spec 0.6.4 литеральные `.` или `:` внутри сегмента ключа
записываются через backslash:

```text
a\.b: v        // key is the single segment "a.b" -> { "a.b": "v" }
a\:b: v        // key contains a colon            -> { "a:b": "v" }
x.y\.z: v      // split on the first dot only     -> { "x": { "y.z": "v" } }
```

Литеральный backslash в ключе пишется как `\\`.

## Как резолвится нативная библиотека

При первом вызове Java-библиотека ищет `ktav_cabi` в таком порядке:

1. **`$KTAV_LIB_PATH`** — абсолютный путь к локальной сборке. Полезно
   для разработки и air-gapped CI.
2. **Кэш пользователя** — `<userCache>/ktav-java/v<версия>/…`,
   скачанный предыдущим вызовом.
3. **Скачивание с GitHub Release** — соответствующий ассет тянется
   один раз с
   `github.com/ktav-lang/java/releases/download/v<версия>/<имя>`
   и кладётся в (2). На первом вызове после установки нужна сеть.

`<userCache>` это `%LOCALAPPDATA%` на Windows, `~/Library/Caches` на
macOS, `$XDG_CACHE_HOME` или `~/.cache` на Linux.

## Поддерживаемые платформы

- JDK 17+ (sealed interfaces).
- Собранные бинарники для: `linux/amd64`, `linux/arm64`, `darwin/amd64`,
  `darwin/arm64`, `windows/amd64`, `windows/arm64`.
- Линукс-дистрибутивы должны использовать glibc 2.17+ (дефолтная цель
  Rust). Поддержка Alpine (musl) — запланирована.

## Лицензия

MIT OR Apache-2.0 — см. [LICENSE-MIT](../../LICENSE-MIT) и [LICENSE-APACHE](../../LICENSE-APACHE).

## Другие реализации Ktav

- [`spec`](https://github.com/ktav-lang/spec) — спецификация + conformance-тесты
- [`rust`](https://github.com/ktav-lang/rust) — эталонный Rust crate (`cargo add ktav`)
- [`csharp`](https://github.com/ktav-lang/csharp) — C# / .NET (`dotnet add package Ktav`)
- [`golang`](https://github.com/ktav-lang/golang) — Go (`go get github.com/ktav-lang/golang`)
- [`js`](https://github.com/ktav-lang/js) — JS / TS (`npm install @ktav-lang/ktav`)
- [`php`](https://github.com/ktav-lang/php) — PHP (`composer require ktav-lang/ktav`)
- [`python`](https://github.com/ktav-lang/python) — Python (`pip install ktav`)
