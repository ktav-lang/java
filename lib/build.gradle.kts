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
}

val pureCheckJar by tasks.registering(Jar::class) {
    group = "verification"
    description = "Packages the compiled pure smoke checker."
    dependsOn(tasks.named("compilePureCheckJava"))
    from(sourceSets["pureCheck"].output)
    archiveClassifier.set("pure-check")
}

val pureJarTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the JUnit-free pure-backend smoke check against the packaged jar without external runtime dependencies."
    dependsOn(tasks.named("jar"), pureCheckJar)
    classpath = files(
        tasks.named<Jar>("jar").flatMap { it.archiveFile },
        pureCheckJar.flatMap { it.archiveFile },
    )
    mainClass.set("lang.ktav.PureSmokeMain")
    environment("KTAV_LIB_PATH", "/nonexistent/ktav-native-guard")
}

tasks.named("assemble") {
    dependsOn(pureCheckJar)
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
    configure(
        JavaLibrary(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = true,
        ),
    )
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
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
