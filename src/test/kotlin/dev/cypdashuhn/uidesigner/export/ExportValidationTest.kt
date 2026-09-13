package dev.cypdashuhn.uidesigner.export

import dev.rooster.region.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExportValidationTest {
    @Test
    fun `blank names are reported as unnamed at their positions`() {
        val validation =
            validateForExport(
                listOf(
                    chest("", BlockPos(0, 0, 0)),
                    chest("   ", BlockPos(1, 0, 0)),
                    chest("Shop", BlockPos(2, 0, 0)),
                ),
            )

        assertEquals(listOf(BlockPos(0, 0, 0), BlockPos(1, 0, 0)), validation.unnamed)
        assertTrue(validation.duplicates.isEmpty())
        assertFalse(validation.isValid)
    }

    @Test
    fun `names differing only by case are one duplicate group`() {
        val validation =
            validateForExport(
                listOf(
                    chest("Shop", BlockPos(0, 0, 0)),
                    chest("shop", BlockPos(1, 0, 0)),
                    chest("SHOP", BlockPos(2, 0, 0)),
                ),
            )

        assertEquals(
            listOf(
                DuplicateNameGroup(
                    listOf(
                        NamedPosition("Shop", BlockPos(0, 0, 0)),
                        NamedPosition("shop", BlockPos(1, 0, 0)),
                        NamedPosition("SHOP", BlockPos(2, 0, 0)),
                    )
                )
            ),
            validation.duplicates,
        )
        assertTrue(validation.unnamed.isEmpty())
    }

    @Test
    fun `names differing only by surrounding whitespace are one duplicate group`() {
        val validation =
            validateForExport(
                listOf(
                    chest(" Shop ", BlockPos(0, 0, 0)),
                    chest("Shop", BlockPos(2, 0, 0)),
                ),
            )

        assertEquals(
            listOf(
                DuplicateNameGroup(
                    listOf(
                        NamedPosition(" Shop ", BlockPos(0, 0, 0)),
                        NamedPosition("Shop", BlockPos(2, 0, 0)),
                    )
                )
            ),
            validation.duplicates,
        )
    }

    @Test
    fun `distinct names are valid`() {
        val validation =
            validateForExport(
                listOf(
                    chest("Shop", BlockPos(0, 0, 0)),
                    chest("Shop 2", BlockPos(1, 0, 0)),
                ),
            )

        assertTrue(validation.isValid)
        assertTrue(validation.unnamed.isEmpty())
        assertTrue(validation.duplicates.isEmpty())
    }

    @Test
    fun `duplicate groups keep first-seen order and each colliding spelling`() {
        val validation =
            validateForExport(
                listOf(
                    chest("Shop", BlockPos(0, 0, 0)),
                    chest("Bank", BlockPos(1, 0, 0)),
                    chest("shop", BlockPos(2, 0, 0)),
                    chest("bank", BlockPos(3, 0, 0)),
                ),
            )

        assertEquals(
            listOf(
                NamedPosition("Shop", BlockPos(0, 0, 0)),
                NamedPosition("shop", BlockPos(2, 0, 0)),
            ),
            validation.duplicates[0].entries,
        )
        assertEquals(
            listOf(
                NamedPosition("Bank", BlockPos(1, 0, 0)),
                NamedPosition("bank", BlockPos(3, 0, 0)),
            ),
            validation.duplicates[1].entries,
        )
    }

    private fun chest(name: String, position: BlockPos) =
        UiChest(name = name, rows = 3, content = emptyList(), position = position)
}
