package examples;

import lang.ktav.Ktav;
import lang.ktav.Value;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * End-to-end demo: parse a Ktav document, pull out typed fields,
 * pattern-match the sealed {@link Value} hierarchy, then build a
 * fresh document in code and render it back to Ktav text.
 *
 * <p>Run from the repo root:
 * <pre>{@code
 *   cargo build --release -p ktav-cabi
 *   KTAV_LIB_PATH="$PWD/target/release/libktav_cabi.so" \
 *       ./gradlew :examples:basic:run
 * }</pre>
 */
public final class Basic {

    private static final String SRC = """
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

    public static void main(String[] args) {
        Value.Obj top = (Value.Obj) Ktav.loads(SRC);

        // ── 1. Pull typed fields out of the parsed document ──────────────
        String  service = str(top, "service");
        long    port    = i64(top, "port");
        double  ratio   = f64(top, "ratio");
        boolean tls     = bool(top, "tls");

        List<String> tags = ((Value.Arr) top.entries().get("tags")).items().stream()
                .map(v -> ((Value.Str) v).value())
                .toList();

        Value.Obj db = (Value.Obj) top.entries().get("db");
        String dbHost    = str(db, "host");
        long   dbTimeout = i64(db, "timeout");

        System.out.printf("service=%s port=%d tls=%s ratio=%.2f%n",
                service, port, tls, ratio);
        System.out.println("tags=" + tags);
        System.out.printf("db: %s (timeout=%ds)%n", dbHost, dbTimeout);

        // ── 2. Walk the document, dispatching on the Value variant ───────
        System.out.println("\nshape:");
        for (var e : top.entries().entrySet()) {
            System.out.printf("  %-12s -> %s%n", e.getKey(), describe(e.getValue()));
        }

        // ── 3. Build a config in code, render it as Ktav text ────────────
        Value.Arr upstreams = new Value.Arr(List.of(
                upstream("a.example", 1080),
                upstream("b.example", 1080),
                upstream("c.example", 1080)
        ));

        LinkedHashMap<String, Value> entries = new LinkedHashMap<>();
        entries.put("name",      new Value.Str("frontend"));
        entries.put("port",      Value.Int.of(8443));
        entries.put("tls",       Value.Bool.TRUE);
        entries.put("ratio",     Value.Flt.of(0.95));
        entries.put("upstreams", upstreams);
        entries.put("notes",     Value.Null.NULL);

        String rendered = Ktav.dumps(new Value.Obj(entries));
        System.out.println("\n--- rendered ---");
        System.out.print(rendered);
    }

    // ── Tiny typed accessors. Real apps would map the document into a
    //    record / DTO instead of probing field-by-field. ────────────────

    private static String  str (Value.Obj o, String k) { return ((Value.Str) o.entries().get(k)).value(); }
    private static long    i64 (Value.Obj o, String k) { return ((Value.Int) o.entries().get(k)).toLong(); }
    private static double  f64 (Value.Obj o, String k) { return ((Value.Flt) o.entries().get(k)).toDouble(); }
    private static boolean bool(Value.Obj o, String k) { return ((Value.Bool) o.entries().get(k)).value(); }

    /** Dispatch on the sealed variant. JDK 21 makes this a switch expression
     *  on the type pattern; on JDK 17 we use {@code instanceof} chaining. */
    private static String describe(Value v) {
        if (v instanceof Value.Null)    return "null";
        if (v instanceof Value.Bool b)  return "bool=" + b.value();
        if (v instanceof Value.Int  i)  return "int=" + i.text();
        if (v instanceof Value.Flt  f)  return "float=" + f.text();
        if (v instanceof Value.Str  s)  return "str=\"" + s.value() + "\"";
        if (v instanceof Value.Arr  a)  return "array(" + a.items().size() + ")";
        if (v instanceof Value.Obj  o)  return "object(" + o.entries().size() + ")";
        throw new AssertionError("unreachable: " + v);
    }

    private static Value.Obj upstream(String host, long port) {
        LinkedHashMap<String, Value> m = new LinkedHashMap<>();
        m.put("host", new Value.Str(host));
        m.put("port", Value.Int.of(port));
        return new Value.Obj(m);
    }

    private Basic() {
    }
}
