plugins {
    `java-library`
}

description = "Ktav configuration format — Java bindings (JNA)"

base {
    archivesName.set("ktav")
}

java {
    withSourcesJar()
    withJavadocJar()
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
