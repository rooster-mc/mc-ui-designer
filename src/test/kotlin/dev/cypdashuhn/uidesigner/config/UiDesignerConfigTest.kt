package dev.cypdashuhn.uidesigner.config

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class UiDesignerConfigTest {
    private val dataFolder = Path.of("/server/plugins/UiDesigner")

    @Test
    fun `missing key resolves to the default in the data folder`() {
        val subject = UiDesignerConfig(config(), dataFolder)

        assertEquals(dataFolder.resolve("design.json"), subject.outputFile)
    }

    @Test
    fun `blank output file resolves to the default in the data folder`() {
        val subject = UiDesignerConfig(config(outputFile = "   "), dataFolder)

        assertEquals(dataFolder.resolve("design.json"), subject.outputFile)
    }

    @Test
    fun `missing defaults raise an error`() {
        val subject = UiDesignerConfig(YamlConfiguration(), dataFolder)

        assertThrows(IllegalStateException::class.java) { subject.outputFile }
    }

    @Test
    fun `relative path resolves against the data folder`() {
        val subject = UiDesignerConfig(config(outputFile = "exports/shop.json"), dataFolder)

        assertEquals(dataFolder.resolve("exports/shop.json"), subject.outputFile)
    }

    @Test
    fun `relative path is normalized`() {
        val subject = UiDesignerConfig(config(outputFile = "exports/../shop.json"), dataFolder)

        assertEquals(dataFolder.resolve("shop.json"), subject.outputFile)
    }

    @Test
    fun `absolute path is used unchanged`() {
        val absolute = Path.of("").toAbsolutePath().resolve("shop.json")
        val subject = UiDesignerConfig(config(outputFile = absolute.toString()), dataFolder)

        assertEquals(absolute, subject.outputFile)
    }

    @Test
    fun `non-string output file resolves to the default in the data folder`() {
        val config = config()
        config.set(UiDesignerConfig.OUTPUT_FILE_KEY, listOf("design.json"))
        val subject = UiDesignerConfig(config, dataFolder)

        assertEquals(dataFolder.resolve("design.json"), subject.outputFile)
    }

    @Test
    fun `missing key is written back from the default`() {
        val config = config()
        val subject = UiDesignerConfig(config, dataFolder)

        assertTrue(subject.writeDefaultOutputIfBlank())
        assertTrue(config.isSet(UiDesignerConfig.OUTPUT_FILE_KEY))
        assertEquals("design.json", config.getString(UiDesignerConfig.OUTPUT_FILE_KEY))
    }

    @Test
    fun `blank key is written back from the default`() {
        val config = config(outputFile = "   ")
        val subject = UiDesignerConfig(config, dataFolder)

        assertTrue(subject.writeDefaultOutputIfBlank())
        assertEquals("design.json", config.getString(UiDesignerConfig.OUTPUT_FILE_KEY))
    }

    @Test
    fun `non-string key is not overwritten`() {
        val config = config()
        config.set(UiDesignerConfig.OUTPUT_FILE_KEY, listOf("design.json"))
        val subject = UiDesignerConfig(config, dataFolder)

        assertFalse(subject.writeDefaultOutputIfBlank())
        assertEquals(
            listOf("design.json"),
            config.get(UiDesignerConfig.OUTPUT_FILE_KEY),
        )
    }

    @Test
    fun `non-string key is reported as unusable`() {
        val config = config()
        config.set(UiDesignerConfig.OUTPUT_FILE_KEY, listOf("design.json"))

        assertTrue(UiDesignerConfig(config, dataFolder).hasUnusableOutputFile())
    }

    @Test
    fun `absent blank and string keys are not reported as unusable`() {
        assertFalse(UiDesignerConfig(config(), dataFolder).hasUnusableOutputFile())
        assertFalse(
            UiDesignerConfig(config(outputFile = "   "), dataFolder).hasUnusableOutputFile(),
        )
        assertFalse(
            UiDesignerConfig(config(outputFile = "custom.json"), dataFolder)
                .hasUnusableOutputFile(),
        )
    }

    @Test
    fun `present key is not overwritten`() {
        val config = config(outputFile = "custom.json")
        val subject = UiDesignerConfig(config, dataFolder)

        assertFalse(subject.writeDefaultOutputIfBlank())
        assertEquals("custom.json", config.getString(UiDesignerConfig.OUTPUT_FILE_KEY))
    }

    @Test
    fun `a raw path resolves against the data folder`() {
        val subject = UiDesignerConfig(config(), dataFolder)

        assertEquals(
            dataFolder.resolve("nested/shop.json"),
            subject.resolvePath("nested/shop.json")
        )
    }

    @Test
    fun `json files lists only json files sorted`(
        @TempDir directory: Path
    ) {
        Files.writeString(directory.resolve("beta.json"), "[]")
        Files.writeString(directory.resolve("alpha.json"), "[]")
        Files.writeString(directory.resolve("notes.txt"), "")
        Files.createDirectory(directory.resolve("nested.json"))
        val subject = UiDesignerConfig(config(), directory)

        assertEquals(listOf("alpha.json", "beta.json"), subject.jsonFiles())
    }

    @Test
    fun `json files is empty when the data folder is missing`(
        @TempDir directory: Path
    ) {
        val subject = UiDesignerConfig(config(), directory.resolve("missing"))

        assertEquals(emptyList<String>(), subject.jsonFiles())
    }

    private fun config(
        outputFile: String? = null,
        defaultOutputFile: String = "design.json",
    ): YamlConfiguration {
        val defaults =
            YamlConfiguration().apply {
                set(UiDesignerConfig.OUTPUT_FILE_KEY, defaultOutputFile)
            }
        return YamlConfiguration().apply {
            setDefaults(defaults)
            if (outputFile != null) set(UiDesignerConfig.OUTPUT_FILE_KEY, outputFile)
        }
    }
}
