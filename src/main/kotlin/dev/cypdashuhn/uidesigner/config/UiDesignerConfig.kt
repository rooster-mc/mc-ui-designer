package dev.cypdashuhn.uidesigner.config

import org.bukkit.configuration.file.FileConfiguration
import java.nio.file.Path

class UiDesignerConfig(
    private val config: FileConfiguration,
    private val dataFolder: Path,
) {
    val outputFile: Path
        get() = resolve(explicitOutputFile() ?: defaultOutputFile())

    fun writeDefaultOutputIfBlank(): Boolean {
        if (explicitOutputFile() != null) return false
        config.set(OUTPUT_FILE_KEY, defaultOutputFile())
        return true
    }

    private fun explicitOutputFile(): String? =
        if (!config.isSet(OUTPUT_FILE_KEY)) {
            null
        } else {
            (config.get(OUTPUT_FILE_KEY) as? String)?.takeIf { it.isNotBlank() }
        }

    private fun defaultOutputFile(): String =
        config.defaults?.getString(OUTPUT_FILE_KEY)
            ?: error("config.yml is missing a default for $OUTPUT_FILE_KEY")

    private fun resolve(raw: String): Path {
        val path = Path.of(raw)
        return (if (path.isAbsolute) path else dataFolder.resolve(path)).normalize()
    }

    companion object {
        const val OUTPUT_FILE_KEY = "output-file"
    }
}
