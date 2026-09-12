plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "UiDesigner"

val roosterRegionDir = rootDir.resolve("../rooster-region")
check(roosterRegionDir.isDirectory) {
    "rooster-region must be checked out beside this repo at ${roosterRegionDir.canonicalPath}"
}

includeBuild(roosterRegionDir) {
    dependencySubstitution {
        substitute(module("dev.rooster.region:rooster-region"))
            .using(project(":core"))
        substitute(module("dev.rooster.region:rooster-region-worldedit"))
            .using(project(":worldedit"))
    }
}

val roosterCommandsDir = rootDir.resolve("../rooster-commands")
check(roosterCommandsDir.isDirectory) {
    "rooster-commands must be checked out beside this repo at ${roosterCommandsDir.canonicalPath}"
}

includeBuild(roosterCommandsDir) {
    dependencySubstitution {
        substitute(module("dev.rooster:rooster-commands"))
            .using(project(":"))
        substitute(module("dev.rooster:command-api"))
            .using(project(":command-api"))
    }
}
