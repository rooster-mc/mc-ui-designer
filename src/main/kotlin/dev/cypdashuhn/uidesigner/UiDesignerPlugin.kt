package dev.cypdashuhn.uidesigner

import org.bukkit.plugin.java.JavaPlugin

// MockBukkit loads plugins by subclassing; Kotlin classes are final by default.
open class UiDesignerPlugin : JavaPlugin() {
    override fun onEnable() {
        logger.info("UiDesigner enabled")
    }

    override fun onDisable() {
        logger.info("UiDesigner disabled")
    }
}
