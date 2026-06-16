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
    mainClass.set("pyre_tutorials.HelloWorldKt")
}

kotlin {
    jvmToolchain(21)
}
