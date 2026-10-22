package lang.ktav;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import lang.ktav.pure.PureKtav;

/**
 * JUnit-free smoke check over the PURE backend, run by the Gradle
 * {@code pureJarTest} task on the PACKAGED jar with ONLY jackson-core
 * on the classpath (no JNA, no JUnit, no test framework). Its whole
 * point is classpath sufficiency: touching only
 * {@link PureKtav} / {@link Value} / {@link KtavException} — and
 * {@code internal.ErrorEnvelope} via its plain {@code of(...)} factory
 * (whose class-initialiser needs jackson-core only) — proves no native
 * resolution path is reachable: {@code internal.NativeLib} /
 * {@code internal.NativeLoader} are never referenced, and the bogus
 * {@code KTAV_LIB_PATH} the task plants in the environment would make
 * any accidental native attempt fail loudly.
 *
 * <p>Prints one {@code PASS <name>} line per check; exits nonzero on
 * the first block of failures so CI fails the job.
 */
public final class PureSmokeMain {

    private PureSmokeMain() {
    }

    public static void main(String[] args) {
        int failures = 0;
        failures += check("bogus KTAV_LIB_PATH override is visible",
                PureSmokeMain::checkEnvGuard);
        failures += check("loads parses a.b to a nested Obj", PureSmokeMain::checkLoadsObj);
        failures += check("loadsStrict accepts canonical and rejects LossyScalar",
                PureSmokeMain::checkStrict);
        failures += check("dumps → loads round trip", PureSmokeMain::checkRoundTrip);
        failures += check("emitCanonical exact §5.9.8 string",
                PureSmokeMain::checkCanonical);
        failures += check("toStringForceStrings emits ::",
                PureSmokeMain::checkForceStrings);
        failures += check("format preserves comments", PureSmokeMain::checkFormat);
        failures += check("runs against the packaged jar, not loose classes",
                PureSmokeMain::checkJarSource);
        failures += check("version is 0.8.1", PureSmokeMain::checkVersion);
        failures += check("loads(byte[]) rejects InvalidUtf8", PureSmokeMain::checkInvalidUtf8);

        if (failures > 0) {
            System.out.println("FAIL pure smoke: " + failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("PASS pure smoke: all checks green");
    }

    // ── checks ──────────────────────────────────────────────────────────

    /** The JavaExec plants a nonexistent KTAV_LIB_PATH; assert we see it. */
    private static void checkEnvGuard() {
        String override = System.getenv("KTAV_LIB_PATH");
        if (override == null || override.isEmpty()) {
            throw new AssertionError(
                    "KTAV_LIB_PATH is not set — the packaged-pure job's"
                            + " nonexistent-path guard is not active");
        }
    }

    private static void checkLoadsObj() {
        Value v = PureKtav.loads("a.b: 1\n");
        if (!(v instanceof Value.Obj obj)) {
            throw new AssertionError("expected Obj root, got: " + v);
        }
        Value aVal = obj.entries().get("a");
        if (!(aVal instanceof Value.Obj aObj)) {
            throw new AssertionError("§5.3: 'a.b' must nest, expected Obj at 'a', got: " + aVal);
        }
        Value bVal = aObj.entries().get("b");
        if (!(bVal instanceof Value.Int bInt) || !bInt.text().equals("1")) {
            throw new AssertionError("expected a.b = Int(1), got: " + bVal);
        }
        // § 5.3: a pair whose value-part is empty has an empty-String
        // value — indentation does NOT nest. Pins the real rule.
        Value empty = PureKtav.loads("outer:\n    inner: 1\n");
        if (!(empty instanceof Value.Obj n
                && n.entries().get("outer") instanceof Value.Str outerStr
                && outerStr.value().isEmpty())) {
            throw new AssertionError("§5.3: 'outer:' with empty value-part must be"
                    + " Str(\"\"), got: " + empty);
        }
        // § 5.3.2: only the dotted key genuinely nests.
        Value dotted = PureKtav.loads("outer.inner: 1\n");
        if (!(dotted instanceof Value.Obj d
                && d.entries().get("outer") instanceof Value.Obj outerObj
                && outerObj.entries().get("inner") instanceof Value.Int one
                && one.text().equals("1"))) {
            throw new AssertionError("§5.3.2: 'outer.inner: 1' must nest to"
                    + " {outer: {inner: Int(1)}}, got: " + dotted);
        }
    }

    private static void checkStrict() {
        Value ok = PureKtav.loadsStrict("a: 1\n");
        if (!(ok instanceof Value.Obj o && o.entries().get("a") instanceof Value.Int i
                && i.text().equals("1"))) {
            throw new AssertionError("loadsStrict must accept canonical input, got: " + ok);
        }
        Value padded = PureKtav.loadsStrict("a: 01\n");
        // §5.2: redundant leading zeros are Strings in BOTH modes — never lossy.
        if (!(padded instanceof Value.Obj p
                && p.entries().get("a") instanceof Value.Str s
                && s.value().equals("01"))) {
            throw new AssertionError("loadsStrict('a: 01') must succeed as Str(\"01\"), got: "
                    + padded);
        }
        try {
            PureKtav.loadsStrict("a: 1.10\n");
            throw new AssertionError("loadsStrict must reject lossy '1.10'");
        } catch (KtavException e) {
            if (!"LossyScalar".equals(e.getError())
                    || !"1.10".equals(e.getBody())
                    || !"1.1".equals(e.getCanonical())) {
                throw new AssertionError(
                        "expected LossyScalar(1.10 → 1.1), got: " + e.getError()
                                + " body=" + e.getBody()
                                + " canonical=" + e.getCanonical());
            }
        }
    }

    private static void checkRoundTrip() {
        LinkedHashMap<String, Value> entries = new LinkedHashMap<>();
        entries.put("name", new Value.Str("ktav"));
        entries.put("count", new Value.Int("3"));
        Value doc = new Value.Obj(entries);
        String text = PureKtav.dumps(doc);
        Value back = PureKtav.loads(text);
        if (!(back instanceof Value.Obj o
                && o.entries().get("name").equals(new Value.Str("ktav"))
                && o.entries().get("count").equals(new Value.Int("3")))) {
            throw new AssertionError("round trip mismatch: " + text + " → " + back);
        }
    }

    private static void checkCanonical() {
        String got = PureKtav.canonicalFromSource("a: 1.50\n");
        // Spec § 5.9.8: shortest round-trip decimal — 1.50 → 1.5
        if (!"a: 1.5\n".equals(got)) {
            throw new AssertionError("canonical of 1.50 must be \"a: 1.5\\n\", got: \""
                    + got + "\"");
        }
        String sci = PureKtav.canonicalFromSource("a: 5e3\n");
        if (!"a: 5000.0\n".equals(sci)) {
            throw new AssertionError("canonical of 5e3 must be \"a: 5000.0\\n\", got: \""
                    + sci + "\"");
        }
    }

    private static void checkForceStrings() {
        String got = PureKtav.toStringForceStrings(
                new Value.Obj(new LinkedHashMap<>(Map.of("n", new Value.Int("7")))));
        if (!got.contains("::")) {
            throw new AssertionError("force-strings output must contain '::': \"" + got + "\"");
        }
    }

    private static void checkFormat() {
        // § 3.4: comments start with exactly '##'; format preserves them.
        String got = PureKtav.format("## header comment\na: 1\n");
        if (!got.contains("## header comment")) {
            throw new AssertionError("format must preserve comments, got: \"" + got + "\"");
        }
        // § 3.4: a single '#' is NOT a comment — '# hash' after ': ' is
        // an ordinary scalar, so the pair value is the String "# hash".
        Value hash = PureKtav.loads("k: # hash\n");
        if (!(hash instanceof Value.Obj h
                && h.entries().get("k") instanceof Value.Str kv
                && kv.value().equals("# hash"))) {
            throw new AssertionError("§3.4: 'k: # hash' must parse as the String"
                    + " \"# hash\", got: " + hash);
        }
    }

    /**
     * Guards against testing library classes directly instead of the
     * packaged jar: {@code PureKtav} must have been loaded from a jar
     * URL whose name contains "ktav". Both runners (the Gradle JavaExec
     * and the direct CI {@code java} command) place the jar FIRST on the
     * classpath, so a green run proves the packaged artifact itself is
     * exercised.
     */
    private static void checkJarSource() {
        java.net.URL location = PureKtav.class.getProtectionDomain()
                .getCodeSource().getLocation();
        String where = String.valueOf(location);
        System.out.println("INFO PureKtav loaded from: " + where);
        if (!where.endsWith(".jar") || !where.contains("ktav")) {
            throw new AssertionError("PureKtav must be loaded from the packaged"
                    + " jar (a 'ktav' *.jar URL), got: " + where);
        }
    }

    private static void checkVersion() {
        if (!"0.8.1".equals(PureKtav.version())) {
            throw new AssertionError("expected version 0.8.1, got: " + PureKtav.version());
        }
    }

    private static void checkInvalidUtf8() {
        byte[] bad = "a: 1\n".getBytes(StandardCharsets.UTF_8);
        byte[] raw = new byte[bad.length + 1];
        System.arraycopy(bad, 0, raw, 0, bad.length);
        raw[bad.length] = (byte) 0xFF;
        try {
            PureKtav.loads(raw);
            throw new AssertionError("loads(byte[]) must reject invalid UTF-8");
        } catch (KtavException e) {
            if (!"InvalidUtf8".equals(e.getError())) {
                throw new AssertionError(
                        "expected InvalidUtf8, got: " + e.getError());
            }
            if (e.getSpanStart() == null || e.getSpanEnd() == null
                    || e.getSpanEnd() <= e.getSpanStart()) {
                throw new AssertionError("InvalidUtf8 span must be non-empty");
            }
        }
    }

    // ── harness ─────────────────────────────────────────────────────────

    private interface Check {
        void run() throws Exception;
    }

    private static int check(String name, Check c) {
        try {
            c.run();
            System.out.println("PASS " + name);
            return 0;
        } catch (Throwable t) {
            System.out.println("FAIL " + name + ": " + t);
            return 1;
        }
    }
}
