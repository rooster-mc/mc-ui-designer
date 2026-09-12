package dev.cypdashuhn.uidesigner

import dev.cypdashuhn.uidesigner.commands.ChestEditCommand
import dev.cypdashuhn.uidesigner.commands.UiDesignerCommand
import dev.cypdashuhn.uidesigner.config.ReloadResult
import dev.cypdashuhn.uidesigner.config.UiDesignerConfig
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIPaperConfig
import dev.rooster.region.Region
import dev.rooster.region.worldedit.toRegion
import dev.rooster.region.worldedit.worldEditSelection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

// MockBukkit loads plugins by subclassing; Kotlin classes are final by default.
open class UiDesignerPlugin : JavaPlugin() {
    lateinit var uiConfig: UiDesignerConfig
        private set

    override fun onLoad() {
        CommandAPI.onLoad(CommandAPIPaperConfig(this))
    }

    override fun onEnable() {
        saveDefaultConfig()
        reloadConfiguration()
        CommandAPI.onEnable()
        ChestEditCommand(this).register()
        UiDesignerCommand(
            plugin = this,
            selectionProvider = { player -> worldEditSelectionOf(player) },
            configProvider = { uiConfig },
            reloadAction = { reloadConfiguration() },
        ).register()
        logger.info(LOG_ENABLED)
    }

    // FAWE is compileOnly and absent from MockBukkit test classpaths; the lambda body only
    // touches the worldedit extension types when invoked, so nothing FAWE-adjacent loads
    // during onEnable or plugin registration.
    private fun worldEditSelectionOf(player: Player): Region? =
        player.worldEditSelection()?.toRegion(player.world)

    fun reloadConfiguration(): ReloadResult {
        val configFile = dataFolder.resolve(CONFIG_FILE_NAME)
        val readable =
            !configFile.isFile ||
                runCatching { YamlConfiguration().load(configFile) }.isSuccess

        reloadConfig()
        val loaded = UiDesignerConfig(config, dataFolder.toPath())
        val result =
            when {
                !readable -> {
                    logger.warning(CONFIG_UNREADABLE_WARNING)
                    ReloadResult.UsingDefaults
                }

                loaded.hasUnusableOutputFile() -> {
                    logger.warning(OUTPUT_PATH_INVALID_WARNING)
                    ReloadResult.InvalidOutput
                }

                else -> {
                    if (loaded.writeDefaultOutputIfBlank()) saveConfig()
                    ReloadResult.Reloaded
                }
            }
        uiConfig = loaded
        return result
    }

    override fun onDisable() {
        CommandAPI.onDisable()
        logger.info(LOG_DISABLED)
    }

    companion object {
        private const val CONFIG_FILE_NAME = "config.yml"
        private const val LOG_ENABLED = "UiDesigner enabled"
        private const val LOG_DISABLED = "UiDesigner disabled"
        private const val CONFIG_UNREADABLE_WARNING =
            "config.yml could not be read; leaving it unchanged"
        private const val OUTPUT_PATH_INVALID_WARNING =
            "${UiDesignerConfig.OUTPUT_FILE_KEY} is not a valid path; leaving it unchanged"
    }
}
