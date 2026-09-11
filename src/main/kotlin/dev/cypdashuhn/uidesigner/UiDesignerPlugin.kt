package dev.cypdashuhn.uidesigner

import dev.cypdashuhn.uidesigner.capture.FaweSelectionSource
import dev.cypdashuhn.uidesigner.capture.SelectionSource
import dev.cypdashuhn.uidesigner.commands.ChestEditCommand
import dev.cypdashuhn.uidesigner.commands.UiDesignerCommand
import dev.cypdashuhn.uidesigner.config.ReloadResult
import dev.cypdashuhn.uidesigner.config.UiDesignerConfig
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIPaperConfig
import dev.rooster.region.Region
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
            selectionSource = faweSelectionSource(),
            configProvider = { uiConfig },
            reloadAction = { reloadConfiguration() },
        ).register()
        logger.info("UiDesigner enabled")
    }

    // FAWE is compileOnly and absent from MockBukkit test classpaths; delegating through
    // this wrapper keeps FaweSelectionSource from loading until a player runs /uidesigner save.
    private fun faweSelectionSource(): SelectionSource =
        object : SelectionSource {
            override fun selectionOf(player: Player): Region? =
                FaweSelectionSource.selectionOf(player)
        }

    fun reloadConfiguration(): ReloadResult {
        val configFile = dataFolder.resolve("config.yml")
        val readable =
            !configFile.isFile ||
                runCatching { YamlConfiguration().load(configFile) }.isSuccess

        reloadConfig()
        val loaded = UiDesignerConfig(config, dataFolder.toPath())
        val result =
            when {
                !readable -> {
                    logger.warning("config.yml could not be read; leaving it unchanged")
                    ReloadResult.UsingDefaults
                }
                loaded.hasUnusableOutputFile() -> {
                    logger.warning(
                        "${UiDesignerConfig.OUTPUT_FILE_KEY} is not a valid path; " +
                            "leaving it unchanged",
                    )
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
        logger.info("UiDesigner disabled")
    }
}
