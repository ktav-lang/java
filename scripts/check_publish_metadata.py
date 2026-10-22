#!/usr/bin/env python3
"""Structural CI-only checker for the GENERATED lib publication artifacts.

Default mode verifies, without publishing, signing, or any secrets:
  - lib/build/publications/maven/pom-default.xml
  - lib/build/publications/maven/module.json
  - lib/build/libs/ktav-0.8.1.jar (exact name)

All checks are structural (stdlib XML/JSON/zipfile parsing), fail-closed, and
print "FAIL: <what>" per defect. Exit 0 only if everything passes.

--self-test mode runs negative controls against committed text fixtures under
scripts/metadata-fixtures/ plus programmatically generated fixture jars in a
temp dir, and asserts the checker DETECTS every defect. It touches only
fixture copies — never the generated artifacts. Exit nonzero if any defect
passes undetected or the good fixture set is rejected.
"""

import argparse
import json
import sys
import tempfile
import zipfile
from pathlib import Path
from xml.etree import ElementTree

GROUP = "io.github.ktav-lang"
ARTIFACT = "ktav"
VERSION = "0.8.1"
POM = "lib/build/publications/maven/pom-default.xml"
MODULE = "lib/build/publications/maven/module.json"
JAR = "lib/build/libs/ktav-0.8.1.jar"

# (group, artifact, version, allowed_scopes) — scope None means omitted-or-compile.
EXPECTED_POM_DEPS = {
    ("net.java.dev.jna", "jna", "5.15.0"): {None, "compile"},
    ("com.fasterxml.jackson.core", "jackson-core", "2.18.2"): {"runtime"},
}

FIXTURES = Path(__file__).resolve().parent / "metadata-fixtures"


class Checker:
    def __init__(self):
        self.failures = []

    def fail(self, what):
        self.failures.append("FAIL: " + what)


# ---------------------------------------------------------------- POM
def _local(tag):
    return tag.rsplit("}", 1)[-1] if isinstance(tag, str) else ""


def _text(el, child):
    for c in el:
        if _local(c.tag) == child:
            return (c.text or "").strip()
    return None


def check_pom(c, path):
    if not path.is_file():
        c.fail("POM: file not found at " + str(path))
        return
    try:
        root = ElementTree.parse(str(path)).getroot()
    except ElementTree.ParseError as e:
        c.fail("POM: not parseable XML: %s" % e)
        return
    if _local(root.tag) != "project":
        c.fail("POM: root element is not <project>")
        return
    # Namespace-agnostic: plugin emits http://maven.apache.org/POM/4.0.0.
    for child in ("groupId", "artifactId", "version"):
        val = _text(root, child)
        expected = {"groupId": GROUP, "artifactId": ARTIFACT, "version": VERSION}[child]
        if val != expected:
            c.fail("POM: %s is %r, expected %r" % (child, val, expected))

    dependency_blocks = [child for child in root if _local(child.tag) == "dependencies"]
    if len(dependency_blocks) != 1:
        c.fail("POM: expected exactly one <dependencies> element")
        return
    deps_el = dependency_blocks[0]

    seen = set()
    jna_found = jackson_found = False
    for dep in deps_el:
        if _local(dep.tag) != "dependency":
            c.fail("POM: unexpected element <%s> inside <dependencies>" % _local(dep.tag))
            continue
        fields = [_local(child.tag) for child in dep]
        if (len(fields) != len(set(fields))
                or set(fields) - {"groupId", "artifactId", "version", "scope", "optional", "type", "classifier"}):
            c.fail("POM: dependency has duplicate or unsupported fields")
        g = _text(dep, "groupId")
        a = _text(dep, "artifactId")
        v = _text(dep, "version")
        scope = _text(dep, "scope")
        key = (g, a, v)
        if key in seen:
            c.fail("POM: duplicate dependency %s:%s:%s" % key)
        seen.add(key)

        allowed_scopes = EXPECTED_POM_DEPS.get(key)
        if allowed_scopes is None:
            c.fail("POM: unexpected dependency %s:%s:%s (exact dependency set violated)" % key)
        else:
            if scope not in allowed_scopes:
                c.fail("POM: %s:%s has scope %r, expected one of %s"
                       % (g, a, scope, sorted(s or "(omitted=compile)" for s in allowed_scopes)))
            if a == "jna":
                jna_found = True
            elif a == "jackson-core":
                jackson_found = True

        optional = _text(dep, "optional")
        if optional not in (None, "false"):
            c.fail("POM: dependency %s:%s has unsupported optional flag" % (g, a))
        for extra in ("type", "classifier"):
            if _text(dep, extra) is not None:
                c.fail("POM: dependency %s:%s sets <%s> (rejected)" % (g, a, extra))

    if not jna_found:
        c.fail("POM: required dependency net.java.dev.jna:jna:5.15.0 missing")
    if not jackson_found:
        c.fail("POM: required dependency com.fasterxml.jackson.core:jackson-core:2.18.2 missing")


# ---------------------------------------------------------------- GMM
def _gmm_variant_deps(c, variant, allowed):
    name = variant.get("name", "?")
    for field in ("available-at", "dependencyConstraints", "capabilities"):
        if field in variant:
            c.fail("GMM: variant %s sets %s (rejected)" % (name, field))
    deps = variant.get("dependencies", [])
    if not isinstance(deps, list):
        c.fail("GMM: variant %s dependencies is not a list" % name)
        return
    keys = []
    for dep in deps:
        if not isinstance(dep, dict) or set(dep) != {"group", "module", "version"}:
            c.fail("GMM: variant %s dependency has unsupported fields" % name)
            continue
        version = dep["version"]
        if (not isinstance(version, dict) or set(version) != {"requires"}
                or not all(isinstance(v, str) and v for v in
                           (dep["group"], dep["module"], version.get("requires")))):
            c.fail("GMM: variant %s dependency has unsupported coordinates/version" % name)
            continue
        keys.append((dep["group"], dep["module"], version["requires"]))
    if len(keys) != len(set(keys)):
        c.fail("GMM: variant %s has duplicate dependencies" % name)
    got = set(keys)
    expected = set(allowed)
    if got != expected:
        c.fail("GMM: variant %s dependency set %s != expected %s"
               % (name, sorted(got), sorted(expected)))


def check_module(c, path):
    if not path.is_file():
        c.fail("GMM: file not found at " + str(path))
        return
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError) as e:
        c.fail("GMM: not parseable JSON: %s" % e)
        return
    if not isinstance(data, dict):
        c.fail("GMM: top-level JSON is not an object")
        return

    if data.get("formatVersion") != "1.1":
        c.fail("GMM: formatVersion is %r, expected '1.1'" % data.get("formatVersion"))
    component = data.get("component")
    expected_component = {"group": GROUP, "module": ARTIFACT, "version": VERSION}
    if (not isinstance(component, dict)
            or any(component.get(k) != v for k, v in expected_component.items())
            or set(component) - {"group", "module", "version", "attributes"}):
        c.fail("GMM: component does not match %r" % expected_component)

    variants = data.get("variants")
    if not isinstance(variants, list):
        c.fail("GMM: variants is not a list")
        return

    by_name = {}
    for v in variants:
        if isinstance(v, dict) and isinstance(v.get("name"), str):
            if v["name"] in by_name:
                c.fail("GMM: duplicate variant %s" % v["name"])
            else:
                by_name[v["name"]] = v
        else:
            c.fail("GMM: variant entry without a string name")

    required = {
        "apiElements": (
            {("net.java.dev.jna", "jna", "5.15.0")},
            {"org.gradle.category": "library", "org.gradle.usage": "java-api"},
        ),
        "runtimeElements": (
            {("net.java.dev.jna", "jna", "5.15.0"),
             ("com.fasterxml.jackson.core", "jackson-core", "2.18.2")},
            {"org.gradle.category": "library", "org.gradle.usage": "java-runtime"},
        ),
    }
    for name, (allowed, req_attrs) in required.items():
        v = by_name.get(name)
        if v is None:
            c.fail("GMM: required variant %s missing" % name)
            continue
        attrs = v.get("attributes", {})
        if not isinstance(attrs, dict):
            c.fail("GMM: variant %s attributes is not an object" % name)
            attrs = {}
        for k, expected in req_attrs.items():
            if attrs.get(k) != expected:
                c.fail("GMM: variant %s attribute %s is %r, expected %r"
                       % (name, k, attrs.get(k), expected))
        _gmm_variant_deps(c, v, allowed)

    allowed_names = set(required)
    for name, v in by_name.items():
        if name in allowed_names:
            continue
        attrs = v.get("attributes", {})
        if not isinstance(attrs, dict) or attrs.get("org.gradle.category") != "documentation":
            c.fail("GMM: unexpected non-documentation variant %s" % name)
        _gmm_variant_deps(c, v, set())


# ---------------------------------------------------------------- JAR
def check_jar(c, path):
    if not path.is_file():
        c.fail("JAR: file not found at " + str(path) + " (exact name required)")
        return
    try:
        with zipfile.ZipFile(str(path)) as zf:
            names = set(zf.namelist())
    except (zipfile.BadZipFile, OSError) as e:
        c.fail("JAR: not a readable zip: %s" % e)
        return
    for entry in ("lang/ktav/Ktav.class", "lang/ktav/pure/PureKtav.class"):
        if entry not in names:
            c.fail("JAR: required entry %s missing from %s" % (entry, path.name))


# ---------------------------------------------------------------- default
def run_default():
    c = Checker()
    root = Path.cwd()
    check_pom(c, root / POM)
    check_module(c, root / MODULE)
    check_jar(c, root / JAR)
    if c.failures:
        for f in c.failures:
            print(f)
        print("FAIL: %d problem(s) in generated publication artifacts" % len(c.failures))
        return 1
    print("OK: generated POM, Gradle Module Metadata, and main jar verified "
          "(coordinates, exact dependency set, no junit, pure backend packaged).")
    return 0


# ---------------------------------------------------------------- self-test
def _mutate_pom(name, mutations, scratch):
    tree = ElementTree.parse(str(FIXTURES / "pom-good.xml"))
    root = tree.getroot()

    def find_deps():
        for child in root:
            if _local(child.tag) == "dependencies":
                return child
        return None

    def strip_ns(el):
        el.tag = _local(el.tag)
        for ch in el:
            strip_ns(ch)

    strip_ns(root)
    deps = find_deps()
    mutations(deps)
    out = Path(tempfile.mkdtemp(prefix="ktav-meta-", dir=str(scratch))) / "pom.xml"
    tree.write(str(out), encoding="unicode")
    return out


def _set_dep(deps, artifact, **fields):
    for d in deps:
        if _text(d, "artifactId") == artifact:
            for k, v in fields.items():
                for ch in list(d):
                    if _local(ch.tag) == k:
                        d.remove(ch)
                if v is not None:
                    e = ElementTree.Element(k)
                    e.text = v
                    d.append(e)
            return d
    return None


def _remove_dep(deps, artifact):
    for d in list(deps):
        if _text(d, "artifactId") == artifact:
            deps.remove(d)
            return


def _make_dep(group, artifact, version, scope=None):
    d = ElementTree.Element("dependency")
    for k, v in (("groupId", group), ("artifactId", artifact), ("version", version)):
        e = ElementTree.Element(k)
        e.text = v
        d.append(e)
    if scope is not None:
        e = ElementTree.Element("scope")
        e.text = scope
        d.append(e)
    return d


def _pom_cases():
    """(case-name, mutation) pairs; mutation gets the <dependencies> element."""
    return [
        ("pom-swapped-scopes", lambda deps: (
            _set_dep(deps, "jna", scope="runtime"),
            _set_dep(deps, "jackson-core", scope="compile"))),
        ("pom-extra-dependency", lambda deps: deps.append(
            _make_dep("org.example", "not-a-real-dep", "1.0.0", scope="runtime"))),
        ("pom-wrong-group", lambda deps: _set_dep(deps, "jna", groupId="com.wrong")),
        ("pom-wrong-version", lambda deps: _set_dep(deps, "jackson-core", version="2.19.0")),
        ("pom-duplicate-dependency", lambda deps: deps.append(
            _make_dep("net.java.dev.jna", "jna", "5.15.0"))),
        ("pom-optional-flag", lambda deps: _set_dep(deps, "jna", optional="true")),
        ("pom-type-set", lambda deps: _set_dep(deps, "jna", type="jar")),
        ("pom-classifier-set", lambda deps: _set_dep(deps, "jna", classifier="tests")),
        ("pom-missing-jna", lambda deps: _remove_dep(deps, "jna")),
        ("pom-junit-leak", lambda deps: deps.append(
            _make_dep("org.junit.jupiter", "junit-jupiter", "5.10.0", scope="test"))),
    ]


def _mutate_module(name, mutations, scratch):
    data = json.loads((FIXTURES / "module-good.json").read_text(encoding="utf-8"))
    mutations(data)
    out = Path(tempfile.mkdtemp(prefix="ktav-meta-", dir=str(scratch))) / "module.json"
    out.write_text(json.dumps(data, indent=2), encoding="utf-8")
    return out


def _variant(data, name):
    for v in data.get("variants", []):
        if v.get("name") == name:
            return v
    return None


def _module_cases():
    def drop_variant(name):
        def m(data):
            data["variants"] = [v for v in data["variants"] if v.get("name") != name]
        return m

    def set_variant_deps(name, deps):
        def m(data):
            _variant(data, name)["dependencies"] = deps
        return m

    def dup_dep(name):
        def m(data):
            v = _variant(data, name)
            v["dependencies"] = v["dependencies"] + [v["dependencies"][0]]
        return m

    def available_at(name):
        def m(data):
            v = _variant(data, name)
            v.pop("dependencies", None)
            v["available-at"] = {"url": "https://example.invalid", "module": "other:thing:1.0"}
        return m

    def platform_attr(name):
        def m(data):
            _variant(data, name)["attributes"]["org.gradle.category"] = "platform"
        return m

    def junit_dep(name):
        def m(data):
            _variant(data, name)["dependencies"].append(
                {"group": "org.junit.jupiter", "module": "junit-jupiter",
                 "version": {"requires": "5.10.0"}})
        return m

    def bad_coordinates(data):
        data["component"]["group"] = "com.wrong"

    def bad_format(data):
        data["formatVersion"] = "1.0"

    return [
        ("gmm-missing-apiElements", drop_variant("apiElements")),
        ("gmm-missing-runtimeElements", drop_variant("runtimeElements")),
        ("gmm-wrong-api-dependencies", set_variant_deps(
            "apiElements", [{"group": "net.java.dev.jna", "module": "jna",
                             "version": {"requires": "5.14.0"}}])),
        ("gmm-wrong-runtime-dependencies", set_variant_deps(
            "runtimeElements", [{"group": "net.java.dev.jna", "module": "jna",
                                 "version": {"requires": "5.15.0"}}])),
        ("gmm-duplicate-dependency", dup_dep("apiElements")),
        ("gmm-duplicate-variant", lambda data: data["variants"].append(
            _variant(data, "apiElements"))),
        ("gmm-dependency-attributes", lambda data:
            _variant(data, "apiElements")["dependencies"][0].update(
                {"attributes": {"org.gradle.category": "platform"}})),
        ("gmm-rich-version", lambda data:
            _variant(data, "apiElements")["dependencies"][0]["version"].update(
                {"strictly": "5.15.0"})),
        ("gmm-runtime-constraints", lambda data:
            _variant(data, "runtimeElements").update({"dependencyConstraints": []})),
        ("gmm-doc-constraints", lambda data:
            _variant(data, "sourcesElements").update({"dependencyConstraints": []})),
        ("gmm-variant-capabilities", lambda data:
            _variant(data, "apiElements").update({"capabilities": []})),
        ("gmm-available-at", available_at("apiElements")),
        ("gmm-platform-attribute", platform_attr("runtimeElements")),
        ("gmm-junit-in-runtime", junit_dep("runtimeElements")),
        ("gmm-doc-variant-with-dependencies", lambda data: data["variants"].append(
            {"name": "javadocElements", "attributes": {"org.gradle.category": "documentation"},
             "dependencies": [{"group": "net.java.dev.jna", "module": "jna",
                               "version": {"requires": "5.15.0"}}]})),
        ("gmm-wrong-coordinates", bad_coordinates),
        ("gmm-wrong-formatVersion", bad_format),
    ]


def _fixture_jars(scratch):
    """Build fixture zips programmatically; returns (good, pure_check_only_dir)."""
    jars = Path(tempfile.mkdtemp(prefix="ktav-jars-", dir=str(scratch)))
    good = jars / ("ktav-%s.jar" % VERSION)
    with zipfile.ZipFile(str(good), "w") as zf:
        zf.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        zf.writestr("lang/ktav/Ktav.class", b"\xca\xfe\xba\xbe-fixture")
        zf.writestr("lang/ktav/pure/PureKtav.class", b"\xca\xfe\xba\xbe-fixture")
    classifier_only = jars / "classifier-only"
    classifier_only.mkdir()
    pure = classifier_only / ("ktav-%s-pure-check.jar" % VERSION)
    with zipfile.ZipFile(str(pure), "w") as zf:
        zf.writestr("lang/ktav/pure/PureKtav.class", b"\xca\xfe\xba\xbe-fixture")
    no_pure = jars / "no-pure.jar"
    with zipfile.ZipFile(str(no_pure), "w") as zf:
        zf.writestr("lang/ktav/Ktav.class", b"\xca\xfe\xba\xbe-fixture")
    return good, classifier_only, no_pure


def run_self_test():
    with tempfile.TemporaryDirectory(prefix="ktav-metadata-controls-") as scratch:
        return _run_self_test(Path(scratch))


def _run_self_test(scratch):
    results = []
    good_jar, jars_dir, no_pure_jar = _fixture_jars(scratch)

    def expect_ok(label, pom, module, jar):
        c = Checker()
        check_pom(c, pom)
        check_module(c, module)
        check_jar(c, jar)
        if c.failures:
            results.append((label, False, "; ".join(c.failures)))
        else:
            results.append((label, True, ""))

    def expect_fail(label, pom, module, jar=None, checker=None):
        c = Checker()
        if checker is not None:
            checker(c)
        elif label.startswith("pom-"):
            check_pom(c, pom)
        elif label.startswith("gmm-"):
            check_module(c, module)
        else:
            raise ValueError("negative control must select its checker: " + label)
        if c.failures:
            results.append((label, True, ""))
        else:
            results.append((label, False, "defect passed UNDETECTED"))

    # Good set must pass (with the fixture good jar).
    expect_ok("good-fixture-set", FIXTURES / "pom-good.xml",
              FIXTURES / "module-good.json", good_jar)

    # POM negative controls.
    for name, mutation in _pom_cases():
        p = _mutate_pom(name, mutation, scratch)
        expect_fail(name, p, FIXTURES / "module-good.json")

    # POM structural garbage.
    garbage = Path(tempfile.mkdtemp(prefix="ktav-meta-", dir=str(scratch))) / "pom.xml"
    garbage.write_text("<not-a-pom/>", encoding="utf-8")
    expect_fail("pom-not-a-project", garbage, FIXTURES / "module-good.json")
    badxml = Path(tempfile.mkdtemp(prefix="ktav-meta-", dir=str(scratch))) / "pom.xml"
    badxml.write_text("<project><unclosed>", encoding="utf-8")
    expect_fail("pom-invalid-xml", badxml, FIXTURES / "module-good.json")

    # GMM negative controls.
    for name, mutation in _module_cases():
        m = _mutate_module(name, mutation, scratch)
        expect_fail(name, FIXTURES / "pom-good.xml", m)
    junk = Path(tempfile.mkdtemp(prefix="ktav-meta-", dir=str(scratch))) / "module.json"
    junk.write_text("{not json", encoding="utf-8")
    expect_fail("gmm-invalid-json", FIXTURES / "pom-good.xml", junk)

    # JAR negative controls.
    expect_fail("jar-missing-main-jar", FIXTURES / "pom-good.xml",
                FIXTURES / "module-good.json",
                checker=lambda c: check_jar(c, jars_dir / ("ktav-%s.jar" % VERSION)))
    expect_fail("jar-lacking-purekav", FIXTURES / "pom-good.xml",
                FIXTURES / "module-good.json", checker=lambda c: check_jar(c, no_pure_jar))
    expect_fail("jar-not-a-zip", FIXTURES / "pom-good.xml",
                FIXTURES / "module-good.json",
                checker=lambda c: check_jar(c, FIXTURES / "pom-good.xml"))

    failed = [r for r in results if not r[1]]
    for label, ok, why in results:
        print("  %s %s%s" % ("PASS" if ok else "SELF-TEST-FAIL", label,
                             (" — " + why) if why else ""))
    if failed:
        print("FAIL: self-test: %d negative control(s) not detected" % len(failed))
        return 1
    print("OK: self-test: %d/%d controls detected correctly." % (len(results), len(results)))
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(description="Structural CI checker for generated publication metadata.")
    ap.add_argument("--self-test", action="store_true",
                    help="run negative controls against scripts/metadata-fixtures/ (CI only)")
    args = ap.parse_args(argv)
    if args.self_test:
        return run_self_test()
    return run_default()


if __name__ == "__main__":
    sys.exit(main())
