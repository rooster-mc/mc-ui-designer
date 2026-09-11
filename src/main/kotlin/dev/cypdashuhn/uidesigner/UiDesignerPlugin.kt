package dev.cypdashuhn.uidesigner

import dev.cypdashuhn.uidesigner.config.UiDesignerConfig
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

// MockBukkit loads plugins by subclassing; Kotlin classes are final by default.
open class UiDesignerPlugin : JavaPlugin() {
    lateinit var uiConfig: UiDesignerConfig
        private set

    override fun onEnable() {
        saveDefaultConfig()
        reloadConfiguration()
        logger.info("UiDesigner enabled")
    }

    fun reloadConfiguration() {
        val configFile = dataFolder.resolve("config.yml")
        val canOverwriteFile =
            !configFile.isFile ||
                runCatching { YamlConfiguration().load(configFile) }.isSuccess

        reloadConfig()
        val loaded = UiDesignerConfig(config, dataFolder.toPath())
        if (canOverwriteFile) {
            if (loaded.writeDefaultOutputIfBlank()) saveConfig()
        } else {
            logger.warning("config.yml could not be read; leaving it unchanged")
        }
        uiConfig = loaded
    }

    override fun onDisable() {
        logger.info("UiDesigner disabled")
    }
}
