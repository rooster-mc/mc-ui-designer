package dev.cypdashuhn.uidesigner.export

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UiChestTest {
    @Test
    fun `a chest survives a JSON round trip`() {
        val chest =
            UiChest(
                name = "Shop",
                rows = 6,
                content =
                    listOf(
                        UiRow(
                            row = 1,
                            slots =
                                listOf(
                                    UiSlot(slot = 1, item = "minecraft:stone", name = "Stone"),
                                    UiSlot(slot = 2, item = "minecraft:diamond"),
                                ),
                        ),
                    ),
            )

        val decoded = DesignJson.decodeFromString<UiChest>(DesignJson.encodeToString(chest))

        assertEquals(chest, decoded)
        assertEquals("Shop", decoded.name)
    }

    @Test
    fun `an empty name is still serialized`() {
        val chest = UiChest(name = "", rows = 3, content = emptyList())

        val json = DesignJson.encodeToString(chest)

        assertTrue(json.contains("\"name\": \"\""))
        assertEquals("", DesignJson.decodeFromString<UiChest>(json).name)
    }

    @Test
    fun `decoding a chest without a name fails`() {
        assertThrows<SerializationException> {
            DesignJson.decodeFromString<UiChest>("""{"rows":3,"content":[]}""")
        }
    }
}
