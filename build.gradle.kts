import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "8.3.6"
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "dev.cypdashuhn"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://maven.enginehub.org/repo/") {
        name = "enginehub-repo"
    }
    maven("https://repo.codemc.io/repository/maven-public/") {
        name = "codemc-repo"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.123-stable")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("dev.jorel:commandapi-paper-shade:11.2.0")

    compileOnly(platform("com.intellectualsites.bom:bom-newest:1.56"))
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.116.1")
    testImplementation("io.papermc.paper:paper-api:26.2.build.111-stable")
}

kotlin {
    jvmToolchain(25)
}

bukkit {
    name = "UiDesigner"
    main = "dev.cypdashuhn.uidesigner.UiDesignerPlugin"
    apiVersion = "26.2"

    commands {
        register("uidesigner") {
            description = "Export a chest design to JSON"
            aliases = listOf("uid")
        }
        register("chest-edit") {
            description = "Name the chest block you are looking at"
        }
    }
}

tasks.processResources {
    filesMatching("config.yml") {
        expand(mapOf("defaultOutput" to project.property("uiDesigner.defaultOutput")))
    }
}

val prepareRunServer =
    tasks.register("prepareRunServer") {
        val runDirectory = layout.projectDirectory.dir("run")
        val serverProperties = runDirectory.file("server.properties")
        val eula = runDirectory.file("eula.txt")
        outputs.files(serverProperties, eula)
        doLast {
            runDirectory.asFile.mkdirs()
            serverProperties.asFile.writeText("server-port=25000\nonline-mode=false\n")
            eula.asFile.writeText("eula=true\n")
        }
    }

tasks.runServer {
    dependsOn(prepareRunServer)
    minecraftVersion("26.2")
    downloadPlugins {
        github(
            "IntellectualSites",
            "FastAsyncWorldEdit",
            "2.15.3",
            "FastAsyncWorldEdit-Paper-2.15.3.jar",
        )
    }
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.test {
    useJUnitPlatform()
}
