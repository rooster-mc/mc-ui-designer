package dev.cypdashuhn.uidesigner.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
    }
}
