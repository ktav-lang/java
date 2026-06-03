# ktav — биндинги для Java

[![Maven Central](https://img.shields.io/maven-central/v/io.github.ktav-lang/ktav?style=flat-square&logo=apachemaven&logoColor=white&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.ktav-lang/ktav)
[![CI](https://img.shields.io/github/actions/workflow/status/ktav-lang/java/ci.yml?style=flat-square&logo=github&label=CI)](https://github.com/ktav-lang/java/actions)
![License: MIT OR Apache-2.0](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue?style=flat-square)
[![Playground](https://img.shields.io/badge/playground-try%20online-7c3aed?style=flat-square&logo=rocket&logoColor=white)](https://ktav-lang.github.io/)

**Languages:** [English](README.md) · **Русский** · [简体中文](README.zh.md)

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
    // пока мы не в Maven Central, забирайте JAR из
    // GitHub Release — пример см. в README.
}

dependencies {
    implementation("io.github.ktav-lang:ktav:0.6.0")
    implementation("net.java.dev.jna:jna:5.15.0")
}
```

### Парсинг — типизированно вытаскиваем поля

```java
import lang.ktav.Ktav;
import lang.ktav.Value;

String src = """
        service: web
        port:i 8080
        ratio:f 0.75
        tls: true
        tags: [
            prod
            eu-west-1
        ]
        db.host: primary.internal
        db.timeout:i 30
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
// port:i 8443
// tls: true
// ratio:f 0.95
// upstreams: [
//     {
//         host: a.example
//         port:i 1080
//     }
// ]
// notes: null
```

Полный запускаемый пример — в [`examples/basic`](examples/basic/src/main/java/examples/Basic.java).

## API

| Функция | Назначение |
| --- | --- |
| `Ktav.loads(String) -> Value` | Разобрать Ktav-документ в дерево `Value`. |
| `Ktav.dumps(Value) -> String` | Отрендерить `Value` обратно в Ktav-текст. Верхний уровень должен быть `Obj`. |
| `Ktav.nativeVersion() -> String` | Версия подгруженного `ktav_cabi`. |

На любой ошибке парсинга/рендеринга бросается `KtavException` —
сообщение это UTF-8 строка от нативного парсера.

## Маппинг типов

Повторяет enum `Value` из Rust-крейта — один вариант на каждый
примитив Ktav, без лоссных приведений:

| Ktav             | вариант `Value`                                         |
| ---------------- | ------------------------------------------------------- |
| `null`           | `Value.Null.NULL`                                       |
| `true` / `false` | `Value.Bool`                                            |
| `:i <digits>`    | `Value.Int` (текстовая форма — произвольная точность, `toBigInteger()` / `toLong()`) |
| `:f <number>`    | `Value.Flt` (текстовая форма — точный round-trip, `toDouble()`) |
| bare scalar      | `Value.Str`                                             |
| `[ ... ]`        | `Value.Arr` (`List<Value>`)                             |
| `{ ... }`        | `Value.Obj` (`LinkedHashMap<String, Value>`, порядок вставки сохранён) |

Типизированные целые и float хранятся **как текст**, чтобы произвольная
точность (цифры сверх `long`) и точное представление десятичного числа
побайтово сохранялись между циклами parse/render.

## Экранирование в ключах

Начиная со spec 0.6.0 литеральные `.` или `:` внутри сегмента ключа
записываются через backslash:

```text
a\.b: v        // ключ — один сегмент "a.b"     -> { "a.b": "v" }
a\:b: v        // двоеточие внутри ключа        -> { "a:b": "v" }
x.y\.z: v      // делим только по первой точке  -> { "x": { "y.z": "v" } }
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

MIT OR Apache-2.0 — см. [LICENSE-MIT](LICENSE-MIT) и [LICENSE-APACHE](LICENSE-APACHE).

## Другие реализации Ktav

- [`spec`](https://github.com/ktav-lang/spec) — спецификация + conformance-тесты
- [`rust`](https://github.com/ktav-lang/rust) — эталонный Rust crate (`cargo add ktav`)
- [`csharp`](https://github.com/ktav-lang/csharp) — C# / .NET (`dotnet add package Ktav`)
- [`golang`](https://github.com/ktav-lang/golang) — Go (`go get github.com/ktav-lang/golang`)
- [`js`](https://github.com/ktav-lang/js) — JS / TS (`npm install @ktav-lang/ktav`)
- [`php`](https://github.com/ktav-lang/php) — PHP (`composer require ktav-lang/ktav`)
- [`python`](https://github.com/ktav-lang/python) — Python (`pip install ktav`)
