plugins {
    application
}

application {
    mainClass.set("examples.Basic")
}

dependencies {
    implementation(project(":lib"))
}
