package dev.cypdashuhn.uidesigner.export

import dev.rooster.region.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReconcileTest {
    @Test
    fun `matching names and contents are in sync`() {
        val report =
            Reconcile.reconcile(
                file = listOf(fileChest("Shop", rows(1 to slot(1, "minecraft:stone", "Stone")))),
                world =
                    listOf(
                        worldChest(
                            "Shop",
                            BlockPos(0, 0, 0),
                            rows(1 to slot(1, "minecraft:stone", "Stone")),
                        ),
                    ),
            )

        assertInstanceOf(ChestStatus.InSync::class.java, report.statuses.single())
        assertTrue(report.isInSync)
    }

    @Test
    fun `a differing slot is updated and carries the difference`() {
        val report =
            Reconcile.reconcile(
                file = listOf(fileChest("Shop", rows(1 to slot(1, "minecraft:stone")))),
                world =
                    listOf(
                        worldChest("Shop", BlockPos(7, 1, 2), rows(1 to slot(1, "minecraft:dirt"))),
                    ),
            )

        val updated = assertInstanceOf(ChestStatus.Updated::class.java, report.statuses.single())
        assertEquals(BlockPos(7, 1, 2), updated.world.position)
        assertEquals(
            listOf(
                SlotDifference(
                    1,
                    1,
                    NormalizedSlot("minecraft:stone", null),
                    NormalizedSlot("minecraft:dirt", null),
                ),
            ),
            updated.drift.differences,
        )
    }

    @Test
    fun `added and removed slots both count as differences`() {
        val report =
            Reconcile.reconcile(
                file = listOf(fileChest("Shop", rows(1 to slot(1, "minecraft:stone")))),
                world =
                    listOf(
                        worldChest(
                            "Shop",
                            BlockPos(0, 0, 0),
                            rows(
                                1 to slot(2, "minecraft:dirt")
                            )
                        )
                    ),
            )

        val updated = assertInstanceOf(ChestStatus.Updated::class.java, report.statuses.single())
        assertEquals(listOf(1, 2), updated.drift.differences.map { it.slot })
        assertEquals(
            null,
            updated.drift.differences
                .first { it.slot == 1 }
                .world
        )
        assertEquals(
            null,
            updated.drift.differences
                .first { it.slot == 2 }
                .file
        )
    }

    @Test
    fun `a file entry with no world chest is missing`() {
        val report =
            Reconcile.reconcile(
                file = listOf(fileChest("Vault", emptyList())),
                world = emptyList(),
            )

        assertEquals(listOf("Vault"), report.missing.map { it.file.name })
        assertTrue(report.statuses.single() is ChestStatus.Missing)
    }

    @Test
    fun `a world chest with no file entry is orphan`() {
        val report =
            Reconcile.reconcile(
                file = emptyList(),
                world = listOf(worldChest("Old", BlockPos(3, 4, 5))),
            )

        val orphan = assertInstanceOf(ChestStatus.Orphan::class.java, report.statuses.single())
        assertEquals("Old", orphan.world.name)
        assertEquals(BlockPos(3, 4, 5), orphan.world.position)
    }

    @Test
    fun `an unnamed world chest is unjoinable`() {
        val report =
            Reconcile.reconcile(
                file = emptyList(),
                world = listOf(worldChest("", BlockPos(1, 2, 3))),
            )

        val unjoinable =
            assertInstanceOf(ChestStatus.Unjoinable::class.java, report.statuses.single())
        assertEquals(UnjoinableReason.UNNAMED, unjoinable.reason)
    }

    @Test
    fun `duplicate world names are unjoinable and the file entry is missing`() {
        val report =
            Reconcile.reconcile(
                file = listOf(fileChest("Shop", emptyList())),
                world =
                    listOf(
                        worldChest("Shop", BlockPos(0, 0, 0)),
                        worldChest("  shop  ", BlockPos(1, 0, 0)),
                    ),
            )

        assertEquals(2, report.unjoinable.size)
        assertTrue(report.unjoinable.all { it.reason == UnjoinableReason.DUPLICATE })
        assertEquals(listOf("Shop"), report.missing.map { it.file.name })
    }

    @Test
    fun `the join trims whitespace and ignores case`() {
        val report =
            Reconcile.reconcile(
                file = listOf(fileChest("  Shop  ", emptyList())),
                world = listOf(worldChest("sHoP", BlockPos(0, 0, 0))),
            )

        val updated = assertInstanceOf(ChestStatus.Updated::class.java, report.statuses.single())
        assertTrue(updated.drift.nameDiffer)
        assertTrue(report.missing.isEmpty())
        assertTrue(report.orphans.isEmpty())
    }

    @Test
    fun `content comparison ignores slot order`() {
        val report =
            Reconcile.reconcile(
                file =
                    listOf(
                        fileChest(
                            "Shop",
                            rows(
                                2 to slot(3, "minecraft:dirt", "Dirt"),
                                1 to slot(1, "minecraft:stone", "Stone"),
                            ),
                        ),
                    ),
                world =
                    listOf(
                        worldChest(
                            "Shop",
                            BlockPos(0, 0, 0),
                            rows(
                                1 to slot(1, "minecraft:stone", "Stone"),
                                2 to slot(3, "minecraft:dirt", "Dirt"),
                            ),
                        ),
                    ),
            )

        assertInstanceOf(ChestStatus.InSync::class.java, report.statuses.single())
    }

    @Test
    fun `an absent slot and a blank item name compare as empty`() {
        val report =
            Reconcile.reconcile(
                file = listOf(fileChest("Shop", rows(1 to slot(1, "minecraft:stone", "  ")))),
                world =
                    listOf(
                        worldChest(
                            "Shop",
                            BlockPos(0, 0, 0),
                            rows(1 to slot(1, "minecraft:stone"))
                        ),
                    ),
            )

        assertInstanceOf(ChestStatus.InSync::class.java, report.statuses.single())
    }

    @Test
    fun `a rows mismatch is structure drift`() {
        val report =
            Reconcile.reconcile(
                file = listOf(fileChest("Shop", emptyList(), rows = 6)),
                world = listOf(worldChest("Shop", BlockPos(0, 0, 0), rows = 3)),
            )

        val updated = assertInstanceOf(ChestStatus.Updated::class.java, report.statuses.single())
        assertTrue(updated.drift.rowsDiffer)
        assertEquals(6, updated.file.rows)
        assertEquals(3, updated.world.rows)
    }

    @Test
    fun `a name spelling mismatch is drift`() {
        val report =
            Reconcile.reconcile(
                file = listOf(fileChest("Shop", emptyList())),
                world = listOf(worldChest("shop", BlockPos(0, 0, 0))),
            )

        val updated = assertInstanceOf(ChestStatus.Updated::class.java, report.statuses.single())
        assertTrue(updated.drift.nameDiffer)
        assertTrue(updated.drift.differences.isEmpty())
    }

    private fun fileChest(name: String, content: List<UiRow>, rows: Int = 3,): UiChest =
        UiChest(name = name, rows = rows, content = content)

    private fun worldChest(
        name: String,
        position: BlockPos,
        content: List<UiRow> = emptyList(),
        rows: Int = 3,
    ): UiChest = UiChest(name = name, rows = rows, content = content, position = position)

    private fun rows(vararg slots: Pair<Int, UiSlot>): List<UiRow> =
        slots.groupBy({ it.first }, { it.second }).map { (row, rowSlots) -> UiRow(row, rowSlots) }

    private fun slot(slot: Int, item: String, name: String? = null): UiSlot =
        UiSlot(slot, item, name)
}
