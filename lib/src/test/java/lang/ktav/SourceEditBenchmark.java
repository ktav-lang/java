package lang.ktav;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standalone source-edit microbenchmark; run directly, not via JUnit. */
public final class SourceEditBenchmark {
    private static final int WARMUP = Integer.getInteger("ktav.bench.warmup", 300);
    private static final int REPEATS = Integer.getInteger("ktav.bench.repeats", 5);
    private static final int OPS = Integer.getInteger("ktav.bench.ops", 1000);
    private static volatile Object sink;
    private SourceEditBenchmark() {}

    private enum Operation { INSERT, REMOVE, RENAME }
    private record Fixture(String name, String source, String path, String newKey,
            String insertPath, Value inserted, String[] expectedSources, Value[] expectedTrees) {}

    private static Value object(Object... pairs) {
        Map<String, Value> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], (Value) pairs[i + 1]);
        return new Value.Obj(map);
    }

    private static Fixture small() {
        String src = "app: {port: 8080, name: demo}\nkeep: true\n";
        return new Fixture("small-inline", src, "app.port", "listen", "app.extra",
                new Value.Str("ready"),
                new String[]{"app: {port: 8080, name: demo, extra: ready}\nkeep: true\n",
                        "app: {name: demo}\nkeep: true\n",
                        "app: {listen: 8080, name: demo}\nkeep: true\n"},
                new Value[]{object("app", object("port", new Value.Int("8080"), "name", new Value.Str("demo"), "extra", new Value.Str("ready")), "keep", new Value.Bool(true)),
                        object("app", object("name", new Value.Str("demo")), "keep", new Value.Bool(true)),
                        object("app", object("listen", new Value.Int("8080"), "name", new Value.Str("demo")), "keep", new Value.Bool(true))});
    }

    private static Fixture large() {
        String source = "service: {\n  host: localhost\n  port: 8080\n  enabled: true\n}\n";
        StringBuilder insert = new StringBuilder("service: {\n  host: localhost\n  port: 8080\n  enabled: true\n  extra: ready\n}\n");
        StringBuilder remove = new StringBuilder("service: {\n  port: 8080\n  enabled: true\n}\n");
        StringBuilder rename = new StringBuilder("service: {\n  address: localhost\n  port: 8080\n  enabled: true\n}\n");
        StringBuilder src = new StringBuilder(source);
        for (int i = 0; i < 300; i++) {
            String row = "item" + i + ": {id: " + i + ", label: entry-" + i + "}\n";
            src.append(row); insert.append(row); remove.append(row); rename.append(row);
        }
        Value[] trees = new Value[3];
        Map<String, Value> items = new LinkedHashMap<>();
        for (int i = 0; i < 300; i++) items.put("item" + i, object("id", new Value.Int(Integer.toString(i)), "label", new Value.Str("entry-" + i)));
        Map<String, Value> serviceInsert = new LinkedHashMap<>();
        serviceInsert.put("host", new Value.Str("localhost")); serviceInsert.put("port", new Value.Int("8080"));
        serviceInsert.put("enabled", new Value.Bool(true)); serviceInsert.put("extra", new Value.Str("ready"));
        Map<String, Value> serviceRemove = new LinkedHashMap<>(); serviceRemove.put("port", new Value.Int("8080")); serviceRemove.put("enabled", new Value.Bool(true));
        Map<String, Value> serviceRename = new LinkedHashMap<>(); serviceRename.put("address", new Value.Str("localhost")); serviceRename.put("port", new Value.Int("8080")); serviceRename.put("enabled", new Value.Bool(true));
        Map<String, Value> t0 = new LinkedHashMap<>(), t1 = new LinkedHashMap<>(), t2 = new LinkedHashMap<>();
        t0.put("service", new Value.Obj(serviceInsert)); t1.put("service", new Value.Obj(serviceRemove)); t2.put("service", new Value.Obj(serviceRename));
        t0.putAll(items); t1.putAll(items); t2.putAll(items);
        trees[0] = new Value.Obj(t0); trees[1] = new Value.Obj(t1); trees[2] = new Value.Obj(t2);
        return new Fixture("large-block", src.toString(), "service.host", "address", "service.extra",
                new Value.Str("ready"), new String[]{insert.toString(), remove.toString(), rename.toString()}, trees);
    }

    private interface Backend {
        String name(); Value parse(String source); String edit(Fixture f, Operation op);
    }
    private static final Backend NATIVE = new Backend() {
        public String name() { return "Ktav-native"; }
        public Value parse(String s) { return Ktav.loads(s); }
        public String edit(Fixture f, Operation op) {
            return switch (op) {
                case INSERT -> Ktav.insertValue(f.source, f.insertPath, f.inserted);
                case REMOVE -> Ktav.removeValue(f.source, f.path);
                case RENAME -> Ktav.renameKey(f.source, f.path, f.newKey);
            };
        }
    };
    private static final Backend PURE = new Backend() {
        public String name() { return "PureKtav"; }
        public Value parse(String s) { return lang.ktav.pure.PureKtav.loads(s); }
        public String edit(Fixture f, Operation op) {
            return switch (op) {
                case INSERT -> lang.ktav.pure.PureKtav.insertValue(f.source, f.insertPath, f.inserted);
                case REMOVE -> lang.ktav.pure.PureKtav.removeValue(f.source, f.path);
                case RENAME -> lang.ktav.pure.PureKtav.renameKey(f.source, f.path, f.newKey);
            };
        }
    };

    private static void verify(Backend b, Fixture f, Operation op, int index) {
        String expected = f.expectedSources[index];
        String actual = b.edit(f, op);
        if (!expected.equals(actual)) throw new AssertionError(b.name() + "/" + f.name + "/" + op + " exact source mismatch\nexpected: " + expected + "\nactual: " + actual);
        Value tree = f.expectedTrees[index];
        if (!tree.equals(b.parse(actual))) throw new AssertionError(b.name() + "/" + f.name + "/" + op + " semantic mismatch (backend parser)");
        Value other = (b == NATIVE ? PURE : NATIVE).parse(actual);
        if (!tree.equals(other)) throw new AssertionError(b.name() + "/" + f.name + "/" + op + " semantic mismatch (other parser)");
    }

    private static void benchmark(Backend b, Fixture f, Operation op) {
        for (int i = 0; i < WARMUP; i++) sink = b.edit(f, op);
    }

    private static void measure(Backend b, Fixture f, Operation op, int repeat) {
        long start = System.nanoTime();
        for (int i = 0; i < OPS; i++) sink = b.edit(f, op);
        long elapsed = System.nanoTime() - start;
        System.out.printf("%-12s %-12s %-6s repeat=%d %10.1f ns/op%n", b.name(), f.name, op.name().toLowerCase(), repeat, (double) elapsed / OPS);
    }

    public static void main(String[] args) {
        if (WARMUP < 0 || REPEATS < 1 || OPS < 1) throw new IllegalArgumentException("warmup >= 0, repeats/ops >= 1 required");
        System.out.printf("JVM=%s %s; OS=%s %s; warmup=%d repeats=%d ops/repeat=%d%n", System.getProperty("java.vm.name"), System.getProperty("java.version"), System.getProperty("os.name"), System.getProperty("os.arch"), WARMUP, REPEATS, OPS);
        Ktav.nativeVersion(); // Resolve/load native library outside all timed regions.
        List<Fixture> fixtures = List.of(small(), large());
        List<Backend> backends = List.of(NATIVE, PURE);
        for (Fixture fixture : fixtures) System.out.printf("fixture=%s source_utf8_bytes=%d%n", fixture.name, fixture.source.getBytes(StandardCharsets.UTF_8).length);
        System.out.println("Warning: single JVM, no forks/JMH; hosted runner noise applies. ns/op is exploratory only; no speedup conclusion.");

        for (Fixture fixture : fixtures) {
            int index = 0;
            for (Operation operation : Operation.values()) {
                for (Backend backend : backends) verify(backend, fixture, operation, index);
                index++;
            }
        }
        for (Fixture fixture : fixtures) for (Operation operation : Operation.values())
            for (Backend backend : backends) benchmark(backend, fixture, operation);

        for (Fixture fixture : fixtures) {
            for (Operation operation : Operation.values()) {
                for (int repeat = 1; repeat <= REPEATS; repeat++) {
                    Backend first = (repeat & 1) == 1 ? NATIVE : PURE;
                    Backend second = first == NATIVE ? PURE : NATIVE;
                    measure(first, fixture, operation, repeat);
                    measure(second, fixture, operation, repeat);
                }
            }
        }
    }
}
