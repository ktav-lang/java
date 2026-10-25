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

Требуется **JDK 17+**. JAR публикуется в **Maven Central** как
`io.github.ktav-lang:ktav` — release-workflow делает это с каждым тегом;
нативный бинарник `ktav_cabi` один раз подгружается в runtime из
соответствующего GitHub Release.

## Быстрый старт

`build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.ktav-lang:ktav:0.8.1")
}
```

При обычном использовании объединённый артефакт разрешает нативные зависимости: JNA 5.15.0 (`api`) и jackson-core 2.18.2 (`implementation`).

Для использования только чистого backend в Gradle отключите транзитивные зависимости и вызывайте `PureKtav`:

```kotlin
implementation("io.github.ktav-lang:ktav:0.8.1") { isTransitive = false }
```

Для runtime чистого backend требуется только JDK 17+. Его парсер, writer, formatter и контейнер `ErrorEnvelope` не используют Jackson, JNA, JUnit, native-код, WASM, подпроцессы, сеть или fallback. Для чистого backend в Maven исключите `net.java.dev.jna:jna` и `com.fasterxml.jackson.core:jackson-core`. Инструменты сборки, тестирования и документации отделены от runtime.

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
| `Ktav.loads(byte[]) -> Value` | Разобрать исходные UTF-8-байты напрямую; некорректный ввод дает `InvalidUtf8`. |
| `Ktav.loadsStrict(String) -> Value` | Разобрать документ со строгой проверкой записи чисел. |
| `Ktav.loadsStrict(byte[]) -> Value` | Строго разобрать исходные UTF-8-байты, сохранив некорректные байты для диагностики `InvalidUtf8`. |
| `Ktav.dumps(Value) -> String` | Отрендерить `Value` обратно в Ktav-текст. Верхний уровень должен быть `Obj` или `Arr`. |
| `Ktav.toStringForceStrings(Value) -> String` | Отрендерить как `dumps`, но привести каждый leaf-скаляр к String. |
| `Ktav.emitCanonical(Value) -> String` | Отрендерить `Value` в детерминированной канонической форме. |
| `Ktav.format(String) -> String` | Нормализовать написание документа, сохраняя комментарии. |
| `Ktav.canonicalFromSource(String) -> String` | Разобрать и заново вывести как канонический Ktav за один вызов — `emitCanonical(loads(src))` без промежуточного `Value`. Комментарии и пустые строки отбрасывает так же, как `emitCanonical`. |
| `Ktav.nativeVersion() -> String` | Версия подгруженного `ktav_cabi`. |

Для raw-byte fixtures передавайте исходный `byte[]` в `loads` или `loadsStrict`, чтобы native-парсер сообщил `InvalidUtf8`; предварительное декодирование может завершиться до вызова Ktav. После добавления перегрузки вызовы `Ktav.loads(null)` и `Ktav.loadsStrict(null)` без приведения неоднозначны при компиляции. Приведение к `String` или `byte[]` выбирает перегрузку; обе при null выбрасывают `NullPointerException("src")`.

`toStringForceStrings` расплющивает целые, float, булевы и `null` в их
текстовую форму через сырой маркер (`::`); объекты и массивы сохраняют
структуру, потому что приводятся только листья. Результат разбирается
обратно через `loads` как тот же набор String-скаляров — полезно, когда
потребитель на выходе не понимает типизированных скаляров.

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

> **Ещё не выпущено (подготовка 0.9):** Эти API редактирования и обновлённая диагностика не входят в опубликованные пакеты 0.8.1. Native-backend требует закреплённое ядро 0.9 (C ABI 2 для C-FFI); pure-backend остаётся самостоятельным.

### Правка исходного текста

`Ktav.editValue()` заменяет **существующее** значение в Ktav-исходном
тексте и возвращает новый исходный текст — это lossless-сплайс на
уровне байтов, а не круг parse→render:

```java
String out = Ktav.editValue("port: 8080\nhost: db\n", "port",
        new Value.Int("9090"));
// port: 9090
// host: db
```

Меняются только байты цели. Комментарии, пустые строки, написание и
порядок ключей, BOM и стиль LF / CR / CRLF вне эффективной правки
сохраняются дословно. Dotted-, inline- и блочные формы переживают
правку как написано; inline-обёртки раскрываются только тогда, когда
замена ухудшает читаемость (многострочная строка, рост строки сверх
`max(100 Unicode scalar values, прежняя длина строки)`, замена,
требующая множества escape-последовательностей), и раскрывается только
затронутая ветвь — нетронутые вложенные ветви остаются inline. Замена,
равная текущему значению, — byte-identical no-op даже для
неканонических написаний скаляров.

Пути — через точку (`key1.key2.0.key4`); внутри сегмента `\.` —
literal точка, `\\` — literal backslash. `editValueSegments` принимает
уже декодированные literal-сегменты: числовой сегмент — это ключ
внутри объекта и zero-based индекс внутри массива. Создание, удаление
и переименование не поддерживаются — только замена существующего
значения.

Ошибки структурированы: `EditInvalidPath` (body: UTF-8 byte offset
внутри аргумента пути, где обнаружен дефект), `EditNotFound`
(отсутствующий ключ, индекс вне диапазона, спуск через скаляр, пустой
список сегментов) и `EditUnsupported` (body: причина — например noncontiguous-цель
из dotted-ключей и reopen, структурная замена, которая потеряла бы
комментарии внутри цели, или значение без lossless-представления в контексте цели).
Проблемы самого исходника по-прежнему приходят обычными parse
envelope'ами. Pure-бэкенд предоставляет те же операции —
`PureKtav.editValue` / `PureKtav.editValueSegments` — с идентичным
выводом.

### Практические примеры правки исходника

Примеры относятся к ещё не выпущенному API 0.9, а не к опубликованному пакету 0.8.1. Они заменяют только существующие значения: не переименовывают ключи, не создают отсутствующие пути и не удаляют записи. Фрагменты независимы; Rust `?` предполагает внешнюю функцию с результатом `Result`. В Go panic используется только для фиксированных корректных данных примера; приложение должно обработать возвращённую ошибку.

Примеры привязок используют самостоятельный pure-бэкенд без native-бинарника. Нативные точки входа принимают те же пути и значения: `ktav.EditValue` (Go), `Ktav.editValue` (Java), `KtavApi.EditValue(..., KtavBackend.Native)` (C#), `Ktav::editValue` (PHP), native/FFI-экспорты пакета (JS), `ktav.edit_value` (Python). C ABI требует версию 2; PyO3/N-API/WASM — совместимое ядро подготовки 0.9. FFI-вызовы Bun/Deno возвращают promise и требуют `await`; показанные pure/Node/WASM-варианты синхронны.

```java
import lang.ktav.Value;
import lang.ktav.pure.PureKtav;
```

#### 1. Настройка развёртывания через dotted-ключ

Меняется только адрес; комментарий, dotted-форма и соседнее поле пользователя сохраняются байт-в-байт.

```java
String source = "## deployment\ntargets.storagehost.address: old\ntargets.storagehost.user: mcp-agent\n";
String updated = PureKtav.editValue(source, "targets.storagehost.address", new Value.Str("192.168.0.88:22"));
```

Результат:

```ktav
## deployment
targets.storagehost.address: 192.168.0.88:22
targets.storagehost.user: mcp-agent
```

#### 2. Буквальные точки: текстовый путь или сегменты

Ключ исходника в кавычках буквально называется `a.b`. В текстовом пути точка экранируется; API сегментов получает декодированное имя напрямую. Оба вызова возвращают одинаковый результат.

```java
String source = "\"a.b\": {port: 80}\n";
String updated = PureKtav.editValue(source, "a\\.b.port", new Value.Int("90"));
String same = PureKtav.editValueSegments(source, new String[] {"a.b", "port"}, new Value.Int("90"));
```

Результат:

```ktav
"a.b": {port: 90}
```

#### 3. Лист внутри массива со смешанными формами

Здесь `0` — индекс массива. В объекте такой же сегмент обозначал бы ключ с именем `0`; нетронутые контейнеры остаются inline.

```java
String source = "servers: [{host: old, keep: yes}, standby]\n";
String updated = PureKtav.editValueSegments(source, new String[] {"servers", "0", "host"}, new Value.Str("new"));
```

Результат:

```ktav
servers: [{host: new, keep: yes}, standby]
```

#### 4. Похожая на число строка остаётся строкой

Замена имеет тип String, а не Integer. Raw-маркер сохраняет это различие. Для числового значения передавайте типизированный Integer.

```java
String source = "port: 8080\n";
String updated = PureKtav.editValue(source, "port", new Value.Str("9090"));
```

Результат:

```ktav
port:: 9090
```

#### 5. Многострочная замена: раскрывается только необходимое

Изменённый массив раскрывается с отступом четыре пробела. Нетронутый объект остаётся inline, содержимое verbatim-строки не переотступается.

```java
String source = "a: [old, {keep: yes}]\n";
String updated = PureKtav.editValue(source, "a.0", new Value.Str("x\ny"));
```

Результат:

```ktav
a: [
    ((
x
y
    ))
    {keep: yes}
]
```

#### 6. No-op сохраняет неканоническое написание

Значение Integer уже равно 1. Проверка равенства возвращает исходные байты, включая `+1` и пробелы разделителя; критерии читаемости не применяются.

```java
String source = "n  : +1\n";
String updated = PureKtav.editValue(source, "n", new Value.Int("1"));
if (!updated.equals(source)) throw new AssertionError("no-op changed source");
```

Результат:

```ktav
n  : +1
```

#### 7. BOM, CRLF и последняя строка без перевода

Результат сохраняет BOM и CRLF, не добавляя финальный перевод строки. Экранированное представление делает эти байты видимыми.

```java
String source = "\ufeffport: 8080\r\nhost: db";
String updated = PureKtav.editValue(source, "port", new Value.Int("9090"));
```

Результат:

```text
"\uFEFFport: 9090\r\nhost: db"
```

Критерии раскрытия фиксированы: многострочная замена, рост сверх `max(100, исходная длина строки)` либо не менее четырёх экранирований с накладными расходами от 25% для отдельной строки замены. Ширина считается в Unicode scalar values, а не байтах UTF-8 или колонках терминала. Уже длинная строка без роста сохраняется. Новые уровни используют четыре пробела и EOL исходника. Если CR или строка данных `))` мешают lossless-кодированию вне inline, сохраняется корректный inline-вариант; эти символы не означают автоматический отказ любой inline-правки.

#### 8. Точная замена Integer

Домен Integer в исходнике — знаковый 64-битный. Это значение больше безопасного диапазона JavaScript Number, но помещается в i64; используйте точное host-представление, а не округлённое число с плавающей точкой. Типизированный Integer вне i64 невозможно сохранить в исходнике как Integer: правка отказывает, не обрезая значение. Для текстового большого идентификатора используйте String. Float различает отрицательный и положительный нуль; no-op для Object не зависит от порядка ключей, порядок Array значим.

```java
String updated = PureKtav.editValue("n: 1\n", "n", new Value.Int("9007199254740993"));
```

```ktav
n: 9007199254740993
```

### Диагностика и безопасные отказы в 0.9

Координаты исходника — байтовые смещения UTF-8 с учётом BOM; spans полуоткрыты `[start, end)`, строки нумеруются с 1. Поддерживаются LF, отдельный CR и CRLF; CRLF считается одним переводом строки. Один Unicode-символ может занимать несколько байтов.

Незавершённый `a: [\n` даёт `UnclosedCompound`, строку 1 и span `3..5`: начало — самая внутренняя незакрытая скобка, а не начало ключа или строки. Для сырых байтов `61 3A 20 FF` (`a: ` и невалидный UTF-8) валидация сообщает `InvalidUtf8`, строку 1 и покрывающий span `3..4`; `line_text` равен null, а не придуманному тексту после lossy-декодирования. Проверка UTF-8 предшествует грамматике.

Некорректный текстовый путь, включая пустой путь или пустой текстовый сегмент, даёт `EditInvalidPath`; смещение считается в байтах UTF-8 самого пути. Пустой список сегментов, отсутствующий ключ, индекс вне диапазона или спуск через скаляр дают `EditNotFound`. В объекте числовой сегмент остаётся ключом; индекс массива содержит ASCII-цифры, допускает ведущие нули, но не знаки.

Целые промежуточные объекты из dotted/reopened-определений могут занимать несколько физических диапазонов и давать `EditUnsupported`; их существующие листья остаются редактируемыми. Структурная замена контейнера не должна удалять внутренние комментарии. Ошибка не возвращает частичный патч и не меняет переданный исходник. Результат повторно разбирается и семантически проверяется: цель должна равняться замене, остальное дерево значений — исходному.

В привязках ошибочный исходник даёт обычный конверт парсера до проверки пути. Некорректный wire-payload — ошибка `Message` без выдуманных координат. Wire-конверт содержит `error`, `reason`, `line`, `line_text`, `span`, `path`, `body`, `canonical`, `spec_section`, `message`; имена host-accessors описаны в разделе ошибок. В `body` находятся десятичное байтовое смещение пути для `EditInvalidPath`, null для `EditNotFound` и причина отказа для `EditUnsupported`.

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

| источник | разбирается как |
| --- | --- |
| `a\.b: v` | `{ "a.b": "v" }` — ключ является одним сегментом `a.b` |
| `a\:b: v` | `{ "a:b": "v" }` — ключ содержит двоеточие |
| `x.y\.z: v` | `{ "x": { "y.z": "v" } }` — разделение только по первой точке; экранированная точка не разделяет |

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

## Чисто-Java бэкенд

Начиная с 0.8.1, библиотека содержит второй, чисто-Java бэкенд в
пакете `lang.ktav.pure` за фасадом `PureKtav`:

| Функция | Назначение |
| --- | --- |
| `PureKtav.loads(byte[])` / `PureKtav.loads(String)` | Разобрать Ktav-документ в дерево `Value`. Вариант с `byte[]` принимает сырые байты и валидирует UTF-8 (§ 6.15) до запуска грамматики; спаны ошибок и номера строк сообщаются по ИСХОДНЫМ сырым байтам (не сдвигаются после пропуска ведущего BOM). |
| `PureKtav.loadsStrict(byte[])` / `PureKtav.loadsStrict(String)` | Разобрать документ со строгой проверкой записи чисел. |
| `PureKtav.dumps(Value)` | Отрендерить `Value` обратно в Ktav-текст. Верхний уровень должен быть `Obj` или `Arr`. |
| `PureKtav.toStringForceStrings(Value)` | Отрендерить как `dumps`, но привести каждый leaf-скаляр к String. |
| `PureKtav.emitCanonical(Value)` | Отрендерить `Value` в детерминированной канонической форме. Канонические числа с плавающей точкой следуют § 5.9.8: кратчайшая десятичная запись с обратимым округлением, научная запись только при `abs >= 1e7` или `abs < 1e-2`. |
| `PureKtav.canonicalFromSource(String)` | Разобрать и заново вывести как канонический Ktav за один вызов. |
| `PureKtav.format(String)` | Нормализовать написание документа, сохраняя комментарии. Переводы строк в выводе — всегда только LF (на входе принимаются LF, CR и CRLF). |
| `PureKtav.version()` | Версия чистого бэкенда — реализованная им версия спецификации. |

### Изолированная установка чистого Java-бэкенда

Сборка объединённого артефакта объявляет JNA 5.15.0 как `api` и jackson-core 2.18.2 как `implementation` для нативного ABI и JSON wire. Стандартное разрешение зависимостей Maven/Gradle включает эти зависимости, поэтому обычная установка не является установкой без зависимостей. Это описание объявлений сборки, а не проверенных опубликованных метаданных.

Для использования только чистого бэкенда в Gradle отключите транзитивные зависимости:

```kotlin
implementation("io.github.ktav-lang:ktav:0.8.1") { isTransitive = false }
```

В Maven исключите `net.java.dev.jna:jna` и `com.fasterxml.jackson.core:jackson-core`; альтернативно используйте JAR библиотеки напрямую. Для выполнения только чистого бэкенда нужен JDK 17, сторонние runtime-библиотеки не нужны. Успешные и ошибочные пути чистого бэкенда не используют JNA, Jackson, JUnit, нативный код, WASM, процессы, сеть или fallback. Нативному бэкенду нужны его зависимости; для него сохраняйте обычное разрешение зависимостей.

Фасад повторяет `Ktav` операция в операцию и спроектирован так, чтобы
давать то же дерево `Value` и те же конверты `KtavException`, поэтому
код, написанный против одного бэкенда, против другого читается без
изменений. Глобального
переключателя бэкендов нет: бэкенд выбирается явно — вызовом `Ktav`
(нативный) или `PureKtav` (чистый).

Чистый режим никогда не загружает и не скачивает нативную библиотеку —
ни JNA, ни `ktav_cabi`, ни кэш-каталога, ни сети. Это правильный выбор
для air-gapped-сред, платформ без готового нативного ассета и всюду,
где важна JVM-only зависимость. Нативный бэкенд остаётся путём,
настроенным по эталонной реализации.

Ограничения чистого бэкенда: глубина вложенности (многострочные рамки
компаундов, inline-рекурсия, длина dotted-путей) ограничена 256
(`PureParser.MAX_DEPTH`), а скаляры живут в доменах i64 / binary64 —
в точности как в нативном ядре. Ограничение действует только при
ПАРСИНГЕ — райтеры итеративны и выводят деревья произвольной глубины
без лимита. Побайтовое каноническое равенство с нативным бэкендом —
это контракт, который тест `CrossBackendTest` проверяет в CI; остальные райтеры следуют
задокументированной раскладке эталонного райтера (отступ в 4 пробела,
порядок вставки, выбор формы ключей по § 5.9.10).

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
