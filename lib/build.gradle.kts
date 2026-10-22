import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.30.0"
}

description = "Ktav configuration format — Java bindings (JNA)"

base {
    archivesName.set("ktav")
}

// pureCheck: JUnit-free smoke-check source set. Declared BEFORE the
// dependencies block so its configurations exist when referenced.
// Compilation wiring is EXPLICIT below: a custom source set does NOT
// see the main output by default, so pureCheck's compile classpath is
// extended with the main source set's classes. EXECUTION, however, is
// deliberately wired to the PACKAGED jar (see pureJarTest / the
// pureCheckDeps copy task) so the smoke main runs against the real
// artifact, never stray build/classes output.
sourceSets {
    create("pureCheck") {
        java {
            srcDir("src/pureCheck/java")
        }
    }
    named("pureCheck") {
        compileClasspath += sourceSets.main.get().output
    }
}

dependencies {
    api(libs.jna)
    implementation(libs.jackson.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // pureCheck: the JUnit-free smoke main (PureSmokeMain). Its runtime
    // classpath is deliberately ONLY the packaged jar + jackson-core —
    // no JNA, no JUnit — so a green run PROVES the pure backend never
    // resolves the native library (NativeLoader is never even loaded,
    // and the task plants a bogus KTAV_LIB_PATH as a tripwire).
    "pureCheckImplementation"(libs.jackson.core)
}

// Copy pureCheck's runtime dependencies (jackson-core only) into
// build/pure-lib/ so CI can run the smoke main with a DIRECT java
// command over "lib/build/libs/*:lib/build/pure-lib/*" — proving the
// classpath sufficiency outside Gradle.
val pureDeps by tasks.registering(Copy::class) {
    group = "verification"
    description = "Copies the pure smoke check's runtime dependencies (jackson-core only) into build/pure-lib/."
    from(configurations["pureCheckRuntimeClasspath"].filter {
        it.name.contains("jackson-core")
    })
    into(layout.buildDirectory.dir("pure-lib"))
}

// Package the COMPILED smoke checker as a JAR into build/pure-check/ so
// the direct CI java run (outside Gradle) can put it on -cp next to the
// packaged jar and pure-lib. Java -cp wildcards expand JAR files ONLY —
// a loose .class package tree under build/pure-check/ is not a valid
// class root, so the checker must ship as an archive (e.g.
// lib/build/pure-check/ktav-0.8.1-pure-check.jar).
val pureCheckJar by tasks.registering(Jar::class) {
    group = "verification"
    description = "Packages the compiled pure smoke checker (PureSmokeMain classes) as a jar."
    dependsOn(tasks.named("compilePureCheckJava"))
    from(sourceSets["pureCheck"].output)
    archiveClassifier.set("pure-check")
}
val pureCheckDeps by tasks.registering(Copy::class) {
    group = "verification"
    description = "Copies the pure smoke checker jar into build/pure-check/."
    dependsOn(pureCheckJar)
    from(pureCheckJar.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("pure-check"))
}

val pureJarTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the JUnit-free pure-backend smoke check against the packaged jar with ONLY jackson-core on the classpath (no JNA — proves no native resolution)."
    // The checker must be compiled, and the library must be PACKAGED:
    // the classpath below deliberately leads with the jar's archive file
    // (not the main output's classes dirs), so the smoke main exercises
    // exactly the artifact we ship. PureSmokeMain additionally asserts
    // at runtime that PureKtav was loaded from a "ktav" *.jar URL.
    dependsOn(tasks.named("jar"), tasks.named("compilePureCheckJava"))
    classpath = files(tasks.named<Jar>("jar").flatMap { it.archiveFile }) +
            sourceSets["pureCheck"].output +
            configurations["pureCheckRuntimeClasspath"]
    mainClass.set("lang.ktav.PureSmokeMain")
    // Tripwire: any accidental native resolution would consult this
    // nonexistent path and fail; PureSmokeMain asserts the env var is
    // visible to document the guarantee.
    environment("KTAV_LIB_PATH", "/nonexistent/ktav-native-guard")
}

tasks.named("assemble") {
    dependsOn(pureDeps, pureCheckDeps)
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Implementation-Title" to "ktav",
            "Implementation-Version" to project.version,
            "Automatic-Module-Name" to "lang.ktav",
        )
    }
}

tasks.named<Javadoc>("javadoc") {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        encoding = "UTF-8"
        charSet = "UTF-8"
    }
}

mavenPublishing {
    // The plugin generates `sources` + `javadoc` jars for us — replaces
    // the earlier `java { withSourcesJar(); withJavadocJar() }` block.
    configure(
        JavaLibrary(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = true,
        ),
    )

    // CENTRAL_PORTAL = the new https://central.sonatype.com workflow.
    // automaticRelease = true tells the portal to promote staging →
    // public Central as soon as validation passes (so we don't need a
    // second manual button-press in the UI).
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)

    // Required by Maven Central — every artifact (jar, sources, javadoc,
    // pom) must carry a detached PGP signature. Key material is fed via
    // env vars at CI time, see release.yml.
    signAllPublications()

    coordinates("io.github.ktav-lang", "ktav", project.version.toString())

    pom {
        name.set("ktav")
        description.set("Java bindings for the Ktav configuration format")
        inceptionYear.set("2026")
        url.set("https://github.com/ktav-lang/java")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("ktav-lang")
                name.set("Ktav Maintainers")
                url.set("https://github.com/ktav-lang")
            }
        }
        scm {
            url.set("https://github.com/ktav-lang/java")
            connection.set("scm:git:git://github.com/ktav-lang/java.git")
            developerConnection.set("scm:git:ssh://git@github.com/ktav-lang/java.git")
        }
    }
}
