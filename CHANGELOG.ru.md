# Changelog

**Языки:** [English](CHANGELOG.md) · **Русский** · [简体中文](CHANGELOG.zh.md)

Все значимые изменения Java-биндинга документируются здесь. Формат
основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/);
версионирование — [Semantic Versioning](https://semver.org/) с pre-1.0
соглашением, что MINOR bump — ломающий.

Этот changelog отслеживает **релизы биндинга**, а не изменения самого
формата Ktav — для последнего см.
[`ktav-lang/spec`](https://github.com/ktav-lang/spec/blob/main/CHANGELOG.md).

## 0.6.4 — 2026-08-23

### Добавлено

- **`Ktav.loadsStrict(String)`** — strict-парсер доступен через Java API и
  символ `ktav_loads_strict` в JNA/C ABI.

### Изменено

- Binding отслеживает `ktav 0.6.4` и spec 0.6.4, включая нормативную
  границу канонической записи float и fixture `notation_boundaries`.
- Загрузчик нативной библиотеки нацелен на точный asset релиза `v0.6.4`.

## [0.6.1] — 2026-06-05

- Документация: все примеры в README переписаны под синтаксис спецификации 0.6 (голые числа вместо удалённых маркеров `:i`/`:f`; комментарии `##` вместо `#`).

## 0.6.0 — 2026-06-01

Синхронизация с Ktav 0.6.0 — ключи теперь поддерживают экранирование.

### Добавлено

- Ключи обрабатывают полный набор escape-последовательностей §3.7,
  включая два новых:
  - `\.` → `.` (литеральная точка — **не** делит dotted-path)
  - `\:` → `:` (литеральное двоеточие — **не** работает как разделитель
    ключ/значение)
- Примеры: `a\.b: v` → `{"a.b": "v"}`, `a\:b: v` → `{"a:b": "v"}`,
  `x.y\.z: v` → `{"x": {"y.z": "v"}}`.

### Ломающие изменения

- Литеральный backslash внутри ключа теперь требует `\\` (раньше `\` в
  ключе был обычным байтом). На практике встречается редко; по pre-1.0
  SemVer — MINOR bump.

### Изменено

- Отслеживает ktav-rust 0.6.0 / Ktav spec 0.6.0. Исходники биндинга не
  менялись — изменение escape-семантики целиком внутри Rust-ядра и
  прозрачно через границу JNA.

---

## 0.1.3 — 2026-05-03

### Изменено

- **Подхватили `ktav 0.1.5`** — в upstream Rust crate появился API
  структурированных ошибок (`Error::Structured(ErrorKind)` с
  byte-offset spans), retroactive `#[non_exhaustive]` на error-enum-ах,
  и публичный event-based парсер `ktav::thin`. Поведение Java-биндинга
  для пользователя не меняется: `KtavException` несёт то же читаемое
  сообщение (Display-строки семи канонических категорий byte-identical
  к ktav 0.1.4 — проверено собственными pinning-тестами ktav). Маппинг
  `ktav::ErrorKind` на структурную Java-иерархию исключений
  (`KtavMissingSeparatorSpaceException`, `KtavDuplicateKeyException`
  и т.д.) — отдельная follow-up работа, описанная в
  [`STRUCTURED_ERRORS.md`](https://github.com/ktav-lang/.github/blob/main/STRUCTURED_ERRORS.md).

Maven Central: `io.github.ktav-lang:ktav:0.1.3`.

## 0.1.2 — 2026-04-26

### Изменено

- **Подхватили `ktav 0.1.4`** — untyped путь `parse() → Value` в
  upstream Rust crate (тот, что использует `cabi`) теперь ~30%
  быстрее на маленьких документах и ~13% на больших, благодаря
  однострочной правке initial capacity для `Frame::Object` (4 → 8).
  Каждый `Ktav.loads` получит ускорение прозрачно.

Maven координаты: `io.github.ktav-lang:ktav:0.1.2`. Подключение:

```kotlin
implementation("io.github.ktav-lang:ktav:0.1.2")
```

## 0.1.1 — Maven Central + правки по ревью

Первая публикация в Maven Central как
`io.github.ktav-lang:ktav:0.1.1`. Подключение:

```kotlin
implementation("io.github.ktav-lang:ktav:0.1.1")
```

### Исправлено

- `Ktav.callNative` оборачивает JNA `Memory` в try-with-resources —
  native-буфер освобождается, даже если FFI-вызов бросит исключение
  (раньше утечка до следующего GC finalizer).
- `Value.Obj` / `Value.Arr` делают defensive copy в compact-
  конструкторе и отдают неизменяемые view'ы — записи претендовали на
  immutable, но через аксессор клиент мог мутировать содержимое.
- `NativeLoader.download` `fsync`-ает байты тела через `WRITE`-
  открытый `FileChannel` перед rename — закрыто окно повреждения
  кеша после crash.
- `NativeLoader.testOverride` — `volatile` для корректной видимости
  при JUnit-параллелизации.
- `ConformanceTest` закрывает `Files.walk`-стрим через try-with-
  resources до возврата `@TestFactory` — фикс утечки directory-
  iterator handle (на Windows проявлялось как `FileSystemException`).
- `SmokeTest.roundTripSimpleDocument` теперь проверяет каждый ключ,
  который кладёт во вход (`ratio`, `nested.inner` молча не
  ассертились).

## 0.1.0 — первый публичный релиз

Первый релиз. Цель — **формат Ktav 0.1**.

### Координаты артефакта

group/name: `io.github.ktav-lang:ktav`. Публикация в Maven Central —
запланирована; пока что JAR выкладывается как ассет GitHub Release.

### Публичный API

- `Ktav.loads(String) -> Value` — разобрать документ Ktav.
- `Ktav.dumps(Value) -> String` — отрендерить `Value` в Ktav-текст.
- `Ktav.nativeVersion() -> String` — версия загруженного `ktav_cabi`.
- `KtavException` — ошибка парсинга/рендера с сообщением от нативной
  стороны.
- `Value` — sealed-интерфейс с семью вариантами (`Null`, `Bool`, `Int`,
  `Flt`, `Str`, `Arr`, `Obj`), повторяет enum `Value` из Rust-крейта.

### Архитектура

- **Нативное ядро** — референсный Rust-крейт `ktav`, обёрнутый тонким
  `extern "C"` C ABI (`crates/cabi`) и распространяемый как
  прекомпилированный `.so` / `.dylib` / `.dll`.
- **Java-лоадер** — JNA (без JNI-компиляции на стороне потребителя):
  библиотека резолвится на первый вызов из `$KTAV_LIB_PATH` или
  скачивается один раз в пользовательский кэш из соответствующего
  GitHub Release asset.
- **Wire-формат** — JSON между Rust и Java с тегированными обёртками
  `{"$i":"..."}` / `{"$f":"..."}` для lossless round-trip типизированных
  integer / float и произвольной точности (`BigInteger`).

### Соответствие типов

| Ktav             | вариант `Value`                                         |
| ---------------- | ------------------------------------------------------- |
| `null`           | `Value.Null.NULL`                                       |
| `true` / `false` | `Value.Bool`                                            |
| `:i <digits>`    | `Value.Int` (текстовая форма — произвольная точность)   |
| `:f <number>`    | `Value.Flt` (текстовая форма — точный round-trip)       |
| scalar без маркера | `Value.Str`                                           |
| `[ ... ]`        | `Value.Arr` (`List<Value>`)                             |
| `{ ... }`        | `Value.Obj` (`LinkedHashMap<String, Value>`)            |

### Платформы

Прекомпилированные нативные бинари:

- `linux/amd64`, `linux/arm64` (glibc)
- `darwin/amd64`, `darwin/arm64`
- `windows/amd64`, `windows/arm64`

Alpine (musl) — в следующем релизе.

### Протестировано на

Полная conformance-сьюта Ktav 0.1 (все `valid/` и `invalid/` фикстуры)
на JDK 17 / 21 × Linux / macOS / Windows.

### Благодарности

Построено поверх reference-Rust-крейта `ktav`. Динамическая загрузка
через [JNA](https://github.com/java-native-access/jna). Streaming JSON
через [Jackson](https://github.com/FasterXML/jackson-core).
