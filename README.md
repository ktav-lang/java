# ktav — Java bindings

[![Maven Central](https://img.shields.io/maven-central/v/io.github.ktav-lang/ktav?style=flat-square&logo=apachemaven&logoColor=white&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.ktav-lang/ktav)
[![CI](https://img.shields.io/github/actions/workflow/status/ktav-lang/java/ci.yml?style=flat-square&logo=github&label=CI)](https://github.com/ktav-lang/java/actions)
![License: MIT OR Apache-2.0](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue?style=flat-square)
[![Playground](https://img.shields.io/badge/playground-try%20online-7c3aed?style=flat-square&logo=rocket&logoColor=white)](https://ktav-lang.github.io/)

**Languages:** **English** · [Русский](docs/ru/README.ru.md) · [简体中文](docs/zh/README.zh.md)

**Playground:** convert JSON / YAML / TOML / INI ⇄ Ktav in your browser at **[ktav-lang.github.io](https://ktav-lang.github.io/)**.

Java bindings for the [Ktav configuration format](https://github.com/ktav-lang/spec).
Thin wrapper around the reference Rust parser, loaded at runtime through
[JNA](https://github.com/java-native-access/jna) — so **no JNI build on
the consumer side**, plain Gradle/Maven just works.

Requires **JDK 17+**. The JAR is published to **Maven Central** as
`io.github.ktav-lang:ktav` by the release workflow on every tag; the
native `ktav_cabi` binary is fetched once at runtime from the matching
GitHub Release.

## Quick start

`build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.ktav-lang:ktav:0.8.1")
}
```

For normal use, the combined artifact resolves its native dependencies: JNA 5.15.0 (`api`) and jackson-core 2.18.2 (`implementation`).

For pure-only Gradle use, disable transitive dependencies and call `PureKtav`:

```kotlin
implementation("io.github.ktav-lang:ktav:0.8.1") { isTransitive = false }
```

Pure runtime requires only JDK 17+. Its parser, writer, formatter, and `ErrorEnvelope` holder use no Jackson, JNA, JUnit, native code, WASM, subprocess, network, or fallback. Maven pure-only consumers must exclude both `net.java.dev.jna:jna` and `com.fasterxml.jackson.core:jackson-core`. Build, test, and documentation tooling is separate from runtime.

### Parse — pull typed fields out of a document

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

### Walk — dispatch on the sealed `Value` hierarchy

```java
for (var e : top.entries().entrySet()) {
    if      (e.getValue() instanceof Value.Bool b) System.out.println(e.getKey() + " is bool=" + b.value());
    else if (e.getValue() instanceof Value.Int  i) System.out.println(e.getKey() + " is int=" + i.text());
    else if (e.getValue() instanceof Value.Arr  a) System.out.println(e.getKey() + " is array(" + a.items().size() + ")");
    // ...Null / Flt / Str / Obj
}
```

On JDK 21+ this becomes a `switch` expression on the sealed type pattern.

### Build & render — construct a document in code

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

A complete runnable version lives in [`examples/basic`](examples/basic/src/main/java/examples/Basic.java).

## API

| Function | Purpose |
| --- | --- |
| `Ktav.loads(String) -> Value` | Parse a Ktav document into the `Value` tree. |
| `Ktav.loads(byte[]) -> Value` | Parse original UTF-8 bytes directly; invalid input is reported as `InvalidUtf8`. |
| `Ktav.loadsStrict(String) -> Value` | Parse with strict numeric spelling checks. |
| `Ktav.loadsStrict(byte[]) -> Value` | Strictly parse original UTF-8 bytes, preserving invalid bytes for `InvalidUtf8` diagnostics. |
| `Ktav.dumps(Value) -> String` | Render a `Value` back as Ktav text. Top-level must be an `Obj` or `Arr`. |
| `Ktav.toStringForceStrings(Value) -> String` | Render like `dumps`, but coerce every leaf scalar to a String. |
| `Ktav.emitCanonical(Value) -> String` | Render a `Value` as deterministic canonical form. |
| `Ktav.format(String) -> String` | Normalise a document's spelling, keeping comments. |
| `Ktav.canonicalFromSource(String) -> String` | Parse and re-emit as canonical Ktav in one call — `emitCanonical(loads(src))` with no intermediate `Value`. Drops comments and blank lines like `emitCanonical` does. |
| `Ktav.nativeVersion() -> String` | Version string reported by the loaded `ktav_cabi`. |

For raw-byte conformance fixtures, pass the original `byte[]` to `loads` or `loadsStrict` so the native parser can report `InvalidUtf8`; decoding a fixture first can reject it before Ktav sees it. Adding the `byte[]` overload means an uncast `Ktav.loads(null)` or `Ktav.loadsStrict(null)` is ambiguous at compile time; cast null to `String` or `byte[]` to select an overload. Either selected overload throws `NullPointerException("src")` for null.

`toStringForceStrings` flattens integers, floats, booleans and `null` to
their textual form via the raw marker (`::`); objects and arrays keep
their structure, since only leaves are coerced. The result parses back
through `loads` as the same set of String scalars — useful when a
downstream consumer does not understand typed scalars.

### Formatting

`Ktav.format()` takes Ktav **source text** and returns Ktav source
text — it is not a `Value` renderer. It normalises structure to
canonical form (§ 5.9) while keeping the trivia the canonical writer
drops:

```java
System.out.print(Ktav.format("## the server\nserver: {host: a, port: 80}\n"));
// ## the server
// server: {
//     host: a
//     port: 80
// }
```

Every comment survives verbatim — Ktav has no trailing comments (§ 3.4:
a comment owns a whole line), so attachment is unambiguous. Blank lines
survive as a grouping hint, but a run of two or more collapses to
exactly one and blank padding just inside a bracket is dropped, which
makes the transform a fixed point: formatting already-formatted text
changes nothing. Key order is never changed — canonical form has no
sorting rule, and reordering keys would make review diffs worse.

For a document with no comments **and no blank lines** the result
equals `Ktav.emitCanonical(Ktav.loads(src))`. The stronger condition is
deliberate: blank lines are no more part of the `Value` model than
comments are, so the canonical writer drops them and `format` does not.

### Errors

`KtavException` is thrown on any parse or render failure. Beyond a
human-readable `getMessage()`, it carries the nine other structured
fields of the core's error envelope through ten accessors — `span` is
split into `getSpanStart()` / `getSpanEnd()` rather than boxed into a
pair type. `getMessage()` IS the envelope's own tenth field, `message`,
taken verbatim — never reassembled from the other nine:

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

The full set is `getError()`, `getReason()`, `getLine()`,
`getLineText()`, `getSpanStart()`, `getSpanEnd()`, `getPath()`,
`getBody()`, `getCanonical()`, `getSpecSection()`. Absent information
is `null` — the boxed `Long` return types exist for exactly that
reason — never a missing accessor, so any field can be read without
first checking the error class.

`getPath()` returns a `List<String>` of **exact decoded key segments,
never a joined string**: a key literally named `a.b` is one segment and
cannot be confused with a two-segment path.

Two writer rejections are named apart — `"UnrepresentableAt"` when the
writer can say which node is at fault (it fills `getPath()` too), and
`"Unrepresentable"` when it cannot. The `reason` code is the same in
both, so matching on `getReason()` is enough when you only need to know
that a write was refused.

## Type mapping

Mirrors the Rust crate's `Value` enum — one variant per Ktav primitive,
no lossy coercions:

| Ktav             | `Value` variant                                         |
| ---------------- | ------------------------------------------------------- |
| `null`           | `Value.Null.NULL`                                       |
| `true` / `false` | `Value.Bool`                                            |
| bare integer     | `Value.Int` (text form — arbitrary precision, `toBigInteger()` / `toLong()`) |
| bare decimal     | `Value.Flt` (text form — exact round-trip, `toDouble()`) |
| other scalar     | `Value.Str`                                             |
| `[ ... ]`        | `Value.Arr` (`List<Value>`)                             |
| `{ ... }`        | `Value.Obj` (`LinkedHashMap<String, Value>`, insertion order preserved) |

Integers and floats are held as **text** so arbitrary precision
(digits beyond `long`) and exact decimal round-trip are preserved byte
for byte across parse/render cycles.

## Key escaping

Since spec 0.6.4 a literal `.` or `:` inside a key segment is written
with a backslash:

| source | parses as |
| --- | --- |
| `a\.b: v` | `{ "a.b": "v" }` — the key is the single segment `a.b` |
| `a\:b: v` | `{ "a:b": "v" }` — the key contains a colon |
| `x.y\.z: v` | `{ "x": { "y.z": "v" } }` — split on the first dot only; the escaped dot does not split |

A literal backslash in a key is `\\`.

## How the native library is resolved

At first call, the Java library resolves `ktav_cabi` in this order:

1. **`$KTAV_LIB_PATH`** — absolute path to a local build. Most useful
   for development and air-gapped CI.
2. **User cache** — `<userCache>/ktav-java/v<version>/…`, downloaded on
   a previous call.
3. **GitHub Release download** — the matching asset is fetched once
   from `github.com/ktav-lang/java/releases/download/v<version>/<name>`
   and cached under (2). Requires network on first call after install.

`<userCache>` is `%LOCALAPPDATA%` on Windows, `~/Library/Caches` on
macOS, `$XDG_CACHE_HOME` or `~/.cache` on Linux.

## The pure-Java backend

Since 0.8.1 the library ships a second, pure-Java backend in the
`lang.ktav.pure` package, behind the `PureKtav` facade:

| Function | Purpose |
| --- | --- |
| `PureKtav.loads(byte[])` / `PureKtav.loads(String)` | Parse a Ktav document into the `Value` tree. The `byte[]` entries take raw bytes and validate UTF-8 (§ 6.15) before the grammar runs; error spans and line numbers are reported over the ORIGINAL raw bytes (they are not rebased after a leading BOM is skipped). |
| `PureKtav.loadsStrict(byte[])` / `PureKtav.loadsStrict(String)` | Parse with strict numeric spelling checks. |
| `PureKtav.dumps(Value)` | Render a `Value` back as Ktav text. Top-level must be an `Obj` or `Arr`. |
| `PureKtav.toStringForceStrings(Value)` | Render like `dumps`, but coerce every leaf scalar to a String. |
| `PureKtav.emitCanonical(Value)` | Render a `Value` as deterministic canonical form. Canonical floats follow § 5.9.8: the shortest round-trip decimal, scientific notation iff `abs >= 1e7` or `abs < 1e-2`. |
| `PureKtav.canonicalFromSource(String)` | Parse and re-emit as canonical Ktav in one call. |
| `PureKtav.format(String)` | Normalise a document's spelling, keeping comments. Output line endings are always LF-only (LF, CR, and CRLF inputs are all accepted). |
| `PureKtav.version()` | Version of the pure backend — the spec version it implements. |

### Isolated pure-Java installation

The combined artifact's build declares JNA 5.15.0 as `api` and jackson-core 2.18.2 as `implementation` for native ABI and JSON-wire support. Standard Maven/Gradle dependency resolution includes those dependencies, so the normal install is not zero-dependency. This describes the build declarations, not verified published metadata.

For pure-only Gradle use, disable transitive dependencies:

```kotlin
implementation("io.github.ktav-lang:ktav:0.8.1") { isTransitive = false }
```

For Maven, exclude both `net.java.dev.jna:jna` and `com.fasterxml.jackson.core:jackson-core`; alternatively, use the library JAR directly. Pure-only runtime use requires JDK 17 and no third-party runtime libraries. Pure success and error paths use no JNA, Jackson, JUnit, native code, WASM, subprocesses, network access, or fallback. Native backend use requires its dependencies; retain normal dependency resolution for it.

The facade mirrors `Ktav` operation-for-operation and is designed to
produce the same `Value` tree and the same `KtavException` envelopes,
so code written against one backend reads unchanged against the other. There is no
global backend switch: you choose a backend explicitly by calling
`Ktav` (native) or `PureKtav` (pure).

Pure mode never loads or downloads a native library — no JNA, no
`ktav_cabi`, no cache directory, no network. It is the right choice for
air-gapped environments, platforms without a prebuilt native asset, and
anywhere a JVM-only dependency matters. The native backend remains the
reference-tuned path.

Limits of the pure backend: nesting depth (multi-line compound frames,
inline recursion, dotted-path length) is bounded by 256
(`PureParser.MAX_DEPTH`), and scalars live in the i64 / binary64
domains, exactly like the native core. The bound applies to PARSING
only — the writers are iterative and emit arbitrarily deep trees
without a depth limit. Byte-for-byte canonical equality with the
native backend is the contract enforced by `CrossBackendTest` in CI; the other writers follow the
reference writer's documented layout (4-space indent, insertion order,
§ 5.9.10 key form selection).

## Runtime support

- JDK 17+ (sealed interfaces).
- Prebuilt binaries for: `linux/amd64`, `linux/arm64`, `darwin/amd64`,
  `darwin/arm64`, `windows/amd64`, `windows/arm64`.
- Linux distros must use glibc 2.17+ (Rust's default target). Alpine
  (musl) support is planned.

## License

MIT OR Apache-2.0 — see [LICENSE-MIT](LICENSE-MIT) and [LICENSE-APACHE](LICENSE-APACHE).

## Other Ktav implementations

- [`spec`](https://github.com/ktav-lang/spec) — specification + conformance suite
- [`rust`](https://github.com/ktav-lang/rust) — reference Rust crate (`cargo add ktav`)
- [`csharp`](https://github.com/ktav-lang/csharp) — C# / .NET (`dotnet add package Ktav`)
- [`golang`](https://github.com/ktav-lang/golang) — Go (`go get github.com/ktav-lang/golang`)
- [`js`](https://github.com/ktav-lang/js) — JS / TS (`npm install @ktav-lang/ktav`)
- [`php`](https://github.com/ktav-lang/php) — PHP (`composer require ktav-lang/ktav`)
- [`python`](https://github.com/ktav-lang/python) — Python (`pip install ktav`)
