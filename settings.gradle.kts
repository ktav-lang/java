plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "ktav"

include("lib")
include("examples:basic")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
