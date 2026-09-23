>>>>> lang=en
## 0.1.0 — first public release

First release. Targets **Ktav format 0.1**.

### Coordinates

Artifact group/name: `io.github.ktav-lang:ktav`. Maven Central
publication is planned; for now JARs ship as GitHub Release assets.

### Public API

- `Ktav.loads(String) -> Value` — parse a Ktav document.
- `Ktav.dumps(Value) -> String` — render a `Value` as Ktav text.
- `Ktav.nativeVersion() -> String` — version of the loaded `ktav_cabi`.
- `KtavException` — parse / render error with the native-side message.
- `Value` — sealed interface with seven variants (`Null`, `Bool`, `Int`,
  `Flt`, `Str`, `Arr`, `Obj`), mirroring the Rust crate's `Value` enum.

### Architecture

- **Native core** — the reference Rust `ktav` crate, wrapped with a tiny
  `extern "C"` C ABI (`crates/cabi`) and distributed as a prebuilt
  `.so` / `.dylib` / `.dll`.
- **Java loader** — JNA (no JNI compilation on the consumer side):
  the library is resolved at first call from `$KTAV_LIB_PATH` or
  downloaded once into the user cache from the matching GitHub Release
  asset.
- **Wire format** — JSON between Rust and Java, with `{"$i":"..."}` /
  `{"$f":"..."}` tagged wrappers for lossless typed-integer / typed-float
  round-trips and arbitrary-precision integers (`BigInteger`).

>>>>> lang=ru
## 0.1.0 — first public release

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

>>>>> lang=zh
## 0.1.0 — first public release

首次发布。目标格式版本:**Ktav 0.1**。

### 构件坐标

group/name:`io.github.ktav-lang:ktav`。Maven Central 发布 ——
已规划;在此之前 JAR 作为 GitHub Release 资产分发。

### 公共 API

- `Ktav.loads(String) -> Value` —— 解析 Ktav 文档。
- `Ktav.dumps(Value) -> String` —— 将 `Value` 渲染为 Ktav 文本。
- `Ktav.nativeVersion() -> String` —— 已加载 `ktav_cabi` 的版本。
- `KtavException` —— 解析/渲染错误,消息来自原生侧。
- `Value` —— 七变体的 sealed 接口 (`Null`、`Bool`、`Int`、`Flt`、
  `Str`、`Arr`、`Obj`),与 Rust crate 的 `Value` 枚举一一对应。

### 架构

- **原生核心** —— 参考 Rust crate `ktav`,通过极简的 `extern "C"` C
  ABI (`crates/cabi`) 封装,分发为预编译的 `.so` / `.dylib` / `.dll`。
- **Java 加载器** —— JNA(使用方无需 JNI 编译):库在首次调用时
  从 `$KTAV_LIB_PATH` 解析,或从对应的 GitHub Release 资产一次性
  下载到用户缓存。
- **Wire 格式** —— Rust 与 Java 之间使用 JSON,带有
  `{"$i":"..."}` / `{"$f":"..."}` 标记包装,实现带类型的
  整数/浮点无损往返及任意精度整数 (`BigInteger`)。

