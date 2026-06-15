plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":"))
}

application {
    mainClass.set("gov.nasa.jpl.pyre.tutorials._01_modeling._01_hello_world.HelloWorldKt")
}

kotlin {
    jvmToolchain(21)
}
