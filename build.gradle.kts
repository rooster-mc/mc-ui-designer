import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission
import org.apache.tools.ant.filters.ReplaceTokens

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

val faweVersion = "2.15.3"

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

    // BOM supplies transitive deps only; the explicit $faweVersion overrides its FAWE 2.15.0.
    compileOnly(platform("com.intellectualsites.bom:bom-newest:1.56"))
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core:$faweVersion")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:$faweVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.116.1")
    testImplementation("dev.jorel:commandapi-paper-test-toolkit:11.2.0")
    testImplementation("dev.jorel:commandapi-paper-core:11.2.0")
    // MockBukkit 4.116.1 targets Paper build 111; main stays on the server build.
    testImplementation("io.papermc.paper:paper-api:26.2.build.111-stable")
}

configurations {
    testImplementation {
        exclude(group = "dev.jorel", module = "commandapi-paper-shade")
    }
    testRuntimeClasspath {
        exclude(group = "dev.jorel", module = "commandapi-paper-shade")
    }
}

kotlin {
    jvmToolchain(25)
}

bukkit {
    name = "UiDesigner"
    main = "dev.cypdashuhn.uidesigner.UiDesignerPlugin"
    apiVersion = "26.2"
    depend = listOf("FastAsyncWorldEdit")
    permissions {
        register("uidesigner.save") {
            description = "Export the selected chest designs to JSON"
            default = Permission.Default.OP
        }
        register("uidesigner.reload") {
            description = "Reload config.yml"
            default = Permission.Default.OP
        }
        register("uidesigner.chest-edit") {
            description = "Name or clear the chest the player is looking at"
            default = Permission.Default.OP
        }
    }
}

tasks.processResources {
    filesMatching("config.yml") {
        // Only ${...} is substituted; stray $ sequences pass through (unlike Groovy expand).
        filter<ReplaceTokens>(
            "tokens" to mapOf("defaultOutput" to project.property("uiDesigner.defaultOutput")),
            "beginToken" to "\${",
            "endToken" to "}",
        )
    }
}

val writeDevServerFiles =
    tasks.register("writeDevServerFiles") {
        val runDirectory = layout.projectDirectory.dir("run")
        val serverProperties = runDirectory.file("server.properties")
        val eula = runDirectory.file("eula.txt")
        outputs.files(serverProperties, eula)
        outputs.upToDateWhen { false }
        doLast {
            runDirectory.asFile.mkdirs()
            serverProperties.asFile.writeText("server-port=25000\nonline-mode=false\n")
            // Accept Mojang's EULA for the local dev server only.
            eula.asFile.writeText("eula=true\n")
        }
    }

tasks.runServer {
    dependsOn(writeDevServerFiles)
    minecraftVersion("26.2")
    downloadPlugins {
        github(
            "IntellectualSites",
            "FastAsyncWorldEdit",
            faweVersion,
            "FastAsyncWorldEdit-Paper-$faweVersion.jar",
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
