# ktav — Java bindings

**Languages:** **English** · [Русский](README.ru.md) · [简体中文](README.zh.md)

Java bindings for the [Ktav configuration format](https://github.com/ktav-lang/spec).
Thin wrapper around the reference Rust parser, loaded at runtime through
[JNA](https://github.com/java-native-access/jna) — so **no JNI build on
the consumer side**, plain Gradle/Maven just works.

Requires **JDK 17+**. Distributed via GitHub Releases for now
(Maven Central publication is planned).

## Quick start

`build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    // while we're not yet on Maven Central, consume the JAR from
    // the GitHub Release — see the README for a worked example.
}

dependencies {
    implementation("io.github.ktav-lang:ktav:0.1.0")
    implementation("net.java.dev.jna:jna:5.15.0")
}
```

### Parse — pull typed fields out of a document

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

A complete runnable version lives in [`examples/basic`](examples/basic/src/main/java/examples/Basic.java).

## API

| Function | Purpose |
| --- | --- |
| `Ktav.loads(String) -> Value` | Parse a Ktav document into the {@link Value} tree. |
| `Ktav.dumps(Value) -> String` | Render a `Value` back as Ktav text. Top-level must be an `Obj`. |
| `Ktav.nativeVersion() -> String` | Version string reported by the loaded `ktav_cabi`. |

`KtavException` is thrown on any parse / render failure; the message is
the UTF-8 string produced by the native parser.

## Type mapping

Mirrors the Rust crate's `Value` enum — one variant per Ktav primitive,
no lossy coercions:

| Ktav             | `Value` variant                                         |
| ---------------- | ------------------------------------------------------- |
| `null`           | `Value.Null.NULL`                                       |
| `true` / `false` | `Value.Bool`                                            |
| `:i <digits>`    | `Value.Int` (text form — arbitrary precision, `toBigInteger()` / `toLong()`) |
| `:f <number>`    | `Value.Flt` (text form — exact round-trip, `toDouble()`) |
| bare scalar      | `Value.Str`                                             |
| `[ ... ]`        | `Value.Arr` (`List<Value>`)                             |
| `{ ... }`        | `Value.Obj` (`LinkedHashMap<String, Value>`, insertion order preserved) |

Typed integers and floats are held as **text** so arbitrary precision
(digits beyond `long`) and exact decimal round-trip are preserved byte
for byte across parse/render cycles.

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

## Runtime support

- JDK 17+ (sealed interfaces).
- Prebuilt binaries for: `linux/amd64`, `linux/arm64`, `darwin/amd64`,
  `darwin/arm64`, `windows/amd64`, `windows/arm64`.
- Linux distros must use glibc 2.17+ (Rust's default target). Alpine
  (musl) support is planned.

## License

MIT — see [LICENSE](LICENSE).

Ktav spec: [ktav-lang/spec](https://github.com/ktav-lang/spec).
Reference Rust crate: [ktav-lang/rust](https://github.com/ktav-lang/rust).
