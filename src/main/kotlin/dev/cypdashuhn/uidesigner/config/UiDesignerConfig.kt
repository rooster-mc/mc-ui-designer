package dev.cypdashuhn.uidesigner.config

import org.bukkit.configuration.file.FileConfiguration
import java.nio.file.Files
import java.nio.file.Path

class UiDesignerConfig(
    private val config: FileConfiguration,
    private val dataFolder: Path,
) {
    val outputFile: Path
        get() = resolvePath(explicitOutputFile() ?: defaultOutputFile())

    fun resolvePath(raw: String): Path {
        val path = Path.of(raw)
        return (if (path.isAbsolute) path else dataFolder.resolve(path)).normalize()
    }

    fun jsonFiles(): List<String> {
        if (!Files.isDirectory(dataFolder)) return emptyList()
        return runCatching {
            Files.list(dataFolder).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.hasJsonExtension() }
                    .map { it.fileName.toString() }
                    .sorted()
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    fun hasUnusableOutputFile(): Boolean =
        config.isSet(OUTPUT_FILE_KEY) && config.get(OUTPUT_FILE_KEY) !is String

    fun writeDefaultOutputIfBlank(): Boolean {
        if (hasUnusableOutputFile()) return false
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

    companion object {
        const val OUTPUT_FILE_KEY = "output-file"
    }
}

private fun Path.hasJsonExtension(): Boolean =
    fileName.toString().endsWith(".json", ignoreCase = true)
