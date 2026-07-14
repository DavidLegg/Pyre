plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "parakeet"

include("tutorials:util")
file("tutorials").listFiles()?.forEach { section ->
    if (section.isDirectory) {
        section.listFiles()?.forEach { lesson ->
            if (lesson.isDirectory) {
                include("tutorials:${section.name}:${lesson.name}")
            }
        }
    }
}
