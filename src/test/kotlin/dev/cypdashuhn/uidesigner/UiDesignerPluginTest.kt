package dev.cypdashuhn.uidesigner

import dev.cypdashuhn.uidesigner.config.UiDesignerConfig
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit

class UiDesignerPluginTest {
    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `plugin loads and enables`() {
        val plugin = MockBukkit.load(UiDesignerPlugin::class.java)

        assertEquals("UiDesigner", plugin.name)
        assertTrue(plugin.isEnabled)
    }

    @Test
    fun `packaged config is written to the data folder on enable`() {
        val plugin = MockBukkit.load(UiDesignerPlugin::class.java)
        val written = plugin.dataFolder.resolve("config.yml")

        assertTrue(written.isFile)
        assertEquals(packagedConfigText(), written.readText())
    }

    @Test
    fun `packaged config has the Gradle default substituted`() {
        assertFalse(packagedConfigText().contains("\${"))
        assertFalse(packagedOutputFile().isBlank())
    }

    @Test
    fun `reload picks up an edited output file`() {
        val plugin = MockBukkit.load(UiDesignerPlugin::class.java)
        plugin.dataFolder.resolve("config.yml").writeText("output-file: custom/shop.json\n")

        plugin.reloadConfiguration()

        assertEquals(
            plugin.dataFolder.toPath().resolve("custom/shop.json"),
            plugin.uiConfig.outputFile,
        )
    }

    @Test
    fun `reload writes back the default when the output key is missing`() {
        val plugin = MockBukkit.load(UiDesignerPlugin::class.java)
        val written = plugin.dataFolder.resolve("config.yml")
        written.writeText("unrelated: true\n")

        plugin.reloadConfiguration()

        assertEquals(
            plugin.dataFolder.toPath().resolve(packagedOutputFile()),
            plugin.uiConfig.outputFile,
        )
        assertTrue(written.readText().contains(UiDesignerConfig.OUTPUT_FILE_KEY))
    }

    @Test
    fun `reload leaves a malformed config file unchanged`() {
        val plugin = MockBukkit.load(UiDesignerPlugin::class.java)
        val written = plugin.dataFolder.resolve("config.yml")
        val malformed = "output-file: [unclosed\n"
        written.writeText(malformed)

        plugin.reloadConfiguration()

        assertEquals(malformed, written.readText())
        assertFalse(plugin.config.isSet(UiDesignerConfig.OUTPUT_FILE_KEY))
    }

    private fun packagedConfigText(): String =
        requireNotNull(javaClass.classLoader.getResource("config.yml")).readText()

    private fun packagedOutputFile(): String =
        YamlConfiguration
            .loadConfiguration(packagedConfigText().reader())
            .getString(UiDesignerConfig.OUTPUT_FILE_KEY)!!
}
