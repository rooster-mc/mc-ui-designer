package dev.cypdashuhn.uidesigner

import dev.cypdashuhn.uidesigner.commands.ChestEditCommand
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIPaperConfig
import org.bukkit.plugin.java.JavaPlugin

// MockBukkit loads plugins by subclassing; Kotlin classes are final by default.
open class UiDesignerPlugin : JavaPlugin() {
    override fun onLoad() {
        CommandAPI.onLoad(CommandAPIPaperConfig(this))
    }

    override fun onEnable() {
        CommandAPI.onEnable()
        ChestEditCommand(this).register()
        logger.info("UiDesigner enabled")
    }

    override fun onDisable() {
        CommandAPI.onDisable()
        logger.info("UiDesigner disabled")
    }
}
