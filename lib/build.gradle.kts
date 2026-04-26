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

dependencies {
    api(libs.jna)
    implementation(libs.jackson.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
