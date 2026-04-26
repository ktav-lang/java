# Changelog

**Languages:** **English** · [Русский](CHANGELOG.ru.md) · [简体中文](CHANGELOG.zh.md)

All notable changes to the Java binding are tracked here. Format based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/) with the pre-1.0 convention
that a MINOR bump is breaking.

This changelog tracks **binding releases**, not changes to the Ktav format
itself — for the latter see
[`ktav-lang/spec`](https://github.com/ktav-lang/spec/blob/main/CHANGELOG.md).

## 0.1.1 — Maven Central + review fixes

First publication to Maven Central as
`io.github.ktav-lang:ktav:0.1.1`. Pull via:

```kotlin
implementation("io.github.ktav-lang:ktav:0.1.1")
```

### Fixed

- `Ktav.callNative` wraps the JNA `Memory` in try-with-resources so
  the native buffer is released even if the FFI call throws (was
  leaking until the next GC finalizer pass).
- `Value.Obj` / `Value.Arr` defensive-copy in their compact
  constructor and expose unmodifiable views — the records were
  advertised as immutable but callers could mutate the entries map
  / items list through the accessor.
- `NativeLoader.download` now `fsync`s the body bytes through a
  `WRITE`-opened `FileChannel` before the rename, eliminating a
  cache-corruption window after crash.
- `NativeLoader.testOverride` is `volatile` for safe publication
  under JUnit parallel execution.
- `ConformanceTest` closes the `Files.walk` stream via
  try-with-resources before returning to `@TestFactory`, fixing a
  directory-iterator handle leak (manifested as `FileSystemException`
  on Windows).
- `SmokeTest.roundTripSimpleDocument` now asserts every key it
  inserts (`ratio`, `nested.inner` were silently unverified).

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

### Type mapping

| Ktav             | `Value` variant                                         |
| ---------------- | ------------------------------------------------------- |
| `null`           | `Value.Null.NULL`                                       |
| `true` / `false` | `Value.Bool`                                            |
| `:i <digits>`    | `Value.Int` (text form — arbitrary precision)           |
| `:f <number>`    | `Value.Flt` (text form — exact round-trip)              |
| bare scalar      | `Value.Str`                                             |
| `[ ... ]`        | `Value.Arr` (`List<Value>`)                             |
| `{ ... }`        | `Value.Obj` (`LinkedHashMap<String, Value>`)            |

### Platforms

Prebuilt native binaries ship for:

- `linux/amd64`, `linux/arm64` (glibc)
- `darwin/amd64`, `darwin/arm64`
- `windows/amd64`, `windows/arm64`

Alpine (musl) is planned for a follow-up.

### Test coverage

Runs the full Ktav 0.1 conformance suite (all `valid/` and `invalid/`
fixtures) on JDK 17 / 21 across Linux / macOS / Windows.

### Credits

Built on top of the reference `ktav` Rust crate. Dynamic loading via
[JNA](https://github.com/java-native-access/jna). JSON streaming via
[Jackson](https://github.com/FasterXML/jackson-core).
