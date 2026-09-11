package dev.cypdashuhn.uidesigner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit

class UiDesignerPluginTest {
    @Test
    fun `plugin loads and enables`() {
        MockBukkit.mock()
        try {
            val plugin = MockBukkit.load(UiDesignerPlugin::class.java)

            assertEquals("UiDesigner", plugin.name)
            assertTrue(plugin.isEnabled)
        } finally {
            MockBukkit.unmock()
        }
    }
}
