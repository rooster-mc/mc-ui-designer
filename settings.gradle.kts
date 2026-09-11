plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "UiDesigner"

val roosterRegionDir = rootDir.resolve("../rooster-region")
check(roosterRegionDir.isDirectory) {
    "rooster-region must be checked out beside this repo at ${roosterRegionDir.canonicalPath}"
}

includeBuild("../rooster-region") {
    dependencySubstitution {
        substitute(module("dev.rooster.region:rooster-region"))
            .using(project(":core"))
        substitute(module("dev.rooster.region:rooster-region-worldedit"))
            .using(project(":worldedit"))
    }
}
