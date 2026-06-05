# Changelog

**Languages:** **English** · [Русский](CHANGELOG.ru.md) · [简体中文](CHANGELOG.zh.md)

All notable changes to the Java binding are tracked here. Format based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/) with the pre-1.0 convention
that a MINOR bump is breaking.

This changelog tracks **binding releases**, not changes to the Ktav format
itself — for the latter see
[`ktav-lang/spec`](https://github.com/ktav-lang/spec/blob/main/CHANGELOG.md).

## [0.6.1] — 2026-06-05

- Docs: rewrite all README examples to spec 0.6 syntax (bare numbers instead of removed `:i`/`:f` markers; `##` comments instead of `#`).

## 0.6.0 — 2026-06-01

Sync to Ktav 0.6.0 — keys now support escaping.

### Added

- Keys process the full §3.7 escape set, with two new escapes:
  - `\.` → `.` (literal dot — does **not** split a dotted path)
  - `\:` → `:` (literal colon — does **not** act as the key/value separator)
- Examples: `a\.b: v` → `{"a.b": "v"}`, `a\:b: v` → `{"a:b": "v"}`,
  `x.y\.z: v` → `{"x": {"y.z": "v"}}`.

### Breaking

- A literal backslash inside a key now requires `\\` (previously `\` in
  a key was a plain byte). Rare in practice; per pre-1.0 SemVer this is
  a MINOR bump.

### Changed

- Tracks ktav-rust 0.6.0 / Ktav spec 0.6.0. Binding source unchanged —
  the escape change is internal to the Rust core and transparent across
  the JNA boundary.

---

## 0.5.0 — 2026-05-27

Implements Ktav spec 0.5.0. Tracks ktav-rust 0.5.0.

### Breaking

- Typed markers `:i` / `:f` removed. Numbers, booleans, and `null` are
  inferred from the lexical form (spec §§ 3.6, 5.2). Write `key: 42` for
  Integer, `key: 3.14` for Float, `key:: 42` to keep a String.
- Comments now use `##` (own line). A single `#` byte is content, not a
  comment.
- Bare integers and floats no longer parse as String — `port: 8080` yields
  `Value.Int("8080")`, not `Value.Str("8080")`.
- Lone `{` / `[` on the first content line opens a multi-line root Object /
  Array; the 0.1.1 JSONL-style semantic is removed.
- Key segments are trimmed of leading/trailing whitespace.

### Added

- **Inline compounds** `{k: v, …}` / `[i, …]` (spec § 5.8) with trailing
  comma, mid-value brace literal (§ 5.8.5), and nesting depth limit of 128.
- **Eight escape sequences** in inline scalars: `\\`, `\,`, `\}`, `\]`,
  `\{`, `\[`, `\n`, `\r` (spec § 3.7).
- **`Ktav.emitCanonical(Value)`** — render a Value as the deterministic
  canonical Ktav form (spec § 7), via the new `ktav_emit_canonical` C ABI
  export.

### Changed

- License: MIT → MIT OR Apache-2.0 (`LICENSE-MIT` + `LICENSE-APACHE`).
- Spec submodule: v0.5.0.
- ktav-rust dependency: 0.5.0.
- Conformance tests now run against `spec/versions/0.5/tests/`.

## 0.3.1 — 2026-05-10

### Added

- **Top-level Array support** (spec § 5.0.1, ktav 0.3.1) — first
  content line decides Object vs Array. `Ktav.loads` now returns
  `Value.Arr` when the document begins with array-item shapes (bare
  scalars, typed/raw markers, lone openers, multi-line openers); all
  prior 0.3.0-valid documents still parse to the same `Value`.
  `Ktav.dumps` accepts a `Value.Arr` at the top level as well.
- **`Ktav.toStringForceStrings(Value)`** — render any Value with every
  leaf scalar (typed integers `:i`, typed floats `:f`, booleans, and
  `null`) coerced to a String via the raw marker (`::`). Compounds
  preserve their structure. Useful for "everything is a string"
  dumps. Round-trips back through `loads` as the same set of String
  scalars.

### Changed

- **Picked up `ktav 0.3.1`** — tracks ktav 0.3.1 (additive). Spec
  submodule synced to `7256816` (spec 0.1.1).

### Native binary cache

- `NativeLoader.LIB_VERSION` bumped to `0.3.1`. Fresh download from the
  matching GitHub Release on first call after upgrade.


## 0.3.0 — 2026-05-08

### Changed

- **Picked up `ktav 0.3.0`** — tracks ktav 0.3.0. Spec submodule synced
  to `46d94a7` (tightened paren-string fixture handling).


## 0.2.0 — 2026-05-07

### Changed (breaking)

- **Picked up `ktav 0.2.0`** — multi-line strings now serialize in the
  indented stripped `( ... )` form by default. `:f 42` accepts integer
  literals (parsed as `42.0`). See the
  [`ktav` crate CHANGELOG](https://github.com/ktav-lang/rust/blob/main/CHANGELOG.md#020--2026-05-07).

  Code comparing serialized output byte-for-byte to a baked-in
  `((...))` literal must be updated. Round-trip is unchanged.

### Spec

- spec submodule synced (typed_float_integer_body fixture; oracle 42.0).


## 0.1.3 — 2026-05-03

### Changed

- **Picked up `ktav 0.1.5`** — the upstream Rust crate now exposes
  `Error::Structured(ErrorKind)` with byte-offset spans, retroactive
  `#[non_exhaustive]` on the error enums, and a public `ktav::thin`
  event-based parser. The Java binding's user-visible behaviour is
  unchanged: `KtavException` carries the same human-readable message
  (Display strings for the seven canonical categories are byte-
  identical to ktav 0.1.4 — verified by ktav's own pinning tests).
  Mapping `ktav::ErrorKind` to a structured Java exception hierarchy
  (`KtavMissingSeparatorSpaceException`, `KtavDuplicateKeyException`,
  etc.) is separate follow-up work tracked in the workspace's
  [`STRUCTURED_ERRORS.md`](https://github.com/ktav-lang/.github/blob/main/STRUCTURED_ERRORS.md).

Maven Central: `io.github.ktav-lang:ktav:0.1.3`.

## 0.1.2 — 2026-04-26

### Changed

- **Picked up `ktav 0.1.4`** — the upstream Rust crate's untyped
  `parse() → Value` path (which `cabi` uses) is now ~30% faster on
  small documents and ~13% faster on large ones, just from a one-
  line `Frame::Object` capacity tweak (4 → 8). Every `Ktav.loads`
  call benefits transparently.

Maven coordinates: `io.github.ktav-lang:ktav:0.1.2`. Pull via:

```kotlin
implementation("io.github.ktav-lang:ktav:0.1.2")
```

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
