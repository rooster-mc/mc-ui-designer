package dev.cypdashuhn.uidesigner.place

import dev.cypdashuhn.uidesigner.export.Reconcile
import dev.cypdashuhn.uidesigner.export.UiChest
import dev.cypdashuhn.uidesigner.export.UiRow
import dev.cypdashuhn.uidesigner.export.UiSlot
import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.rooster.region.BlockPos
import dev.rooster.region.Region
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.world.WorldMock

class ChestSyncerTest {
    private lateinit var server: ServerMock
    private lateinit var world: WorldMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        world = server.addSimpleWorld("world")
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `sync writes the file contents and item name into a chest`() {
        blockAt(0, 0, 0)
        val desired = file("Shop", content(slot(1, "minecraft:stone", "Stone")))

        val result = sync(listOf(desired), listOf(worldChest("Shop", BlockPos(0, 0, 0))))

        assertEquals(ChestSyncer.SyncResult(applied = 1, contentsSkipped = 0), result)
        assertEquals(Material.STONE, inventoryAt(0, 0, 0).getItem(0)?.type)
        assertEquals("Stone", inventoryAt(0, 0, 0).getItem(0)?.plainDisplayName())
    }

    @Test
    fun `sync writes the chest name from the file`() {
        ChestNamer.setName(blockAt(0, 0, 0), "shop")

        sync(listOf(file("Shop")), listOf(worldChest("shop", BlockPos(0, 0, 0))))

        assertEquals("Shop", ChestNamer.nameOf(blockAt(0, 0, 0)))
    }

    @Test
    fun `sync clears slots absent from the file`() {
        blockAt(0, 0, 0)
        inventoryAt(0, 0, 0).setItem(5, ItemStack(Material.DIAMOND))

        sync(
            listOf(file("Shop", content(slot(1, "minecraft:stone")))),
            listOf(worldChest("Shop", BlockPos(0, 0, 0))),
        )

        assertEquals(Material.STONE, inventoryAt(0, 0, 0).getItem(0)?.type)
        assertNull(inventoryAt(0, 0, 0).getItem(5))
    }

    @Test
    fun `sync leaves orphans untouched`() {
        blockAt(0, 0, 0)
        ChestNamer.setName(blockAt(2, 0, 0), "Old")
        inventoryAt(2, 0, 0).setItem(0, ItemStack(Material.DIAMOND))

        sync(
            listOf(file("Shop")),
            listOf(worldChest("Shop", BlockPos(0, 0, 0)), worldChest("Old", BlockPos(2, 0, 0))),
        )

        assertEquals("Old", ChestNamer.nameOf(blockAt(2, 0, 0)))
        assertEquals(Material.DIAMOND, inventoryAt(2, 0, 0).getItem(0)?.type)
    }

    @Test
    fun `sync skips contents but writes the name on a rows mismatch`() {
        ChestNamer.setName(blockAt(0, 0, 0), "shop")
        val desired = file("Shop", content(slot(1, "minecraft:stone")), rows = 6)

        val result = sync(listOf(desired), listOf(worldChest("shop", BlockPos(0, 0, 0))))

        assertEquals(ChestSyncer.SyncResult(applied = 1, contentsSkipped = 1), result)
        assertEquals("Shop", ChestNamer.nameOf(blockAt(0, 0, 0)))
        assertNull(inventoryAt(0, 0, 0).getItem(0))
    }

    @Test
    fun `sync writes contents when only the item names differ`() {
        blockAt(0, 0, 0)
        val desired = file("Shop", content(slot(1, "minecraft:stone", "Polished")))

        val result = sync(listOf(desired), listOf(worldChest("Shop", BlockPos(0, 0, 0))))

        assertEquals(ChestSyncer.SyncResult(applied = 1, contentsSkipped = 0), result)
        assertEquals("Polished", inventoryAt(0, 0, 0).getItem(0)?.plainDisplayName())
    }

    private fun sync(file: List<UiChest>, worldChests: List<UiChest>): ChestSyncer.SyncResult =
        ChestSyncer.apply(region(), Reconcile.reconcile(file, worldChests))

    private fun file(name: String, content: List<UiRow> = emptyList(), rows: Int = 3,): UiChest =
        UiChest(name = name, rows = rows, content = content)

    private fun worldChest(name: String, position: BlockPos, rows: Int = 3): UiChest =
        UiChest(name = name, rows = rows, content = emptyList(), position = position)

    private fun content(vararg slots: UiSlot): List<UiRow> = listOf(UiRow(1, slots.toList()))

    private fun slot(slot: Int, item: String, name: String? = null): UiSlot =
        UiSlot(slot, item, name)

    private fun blockAt(x: Int, y: Int, z: Int): Block =
        world.getBlockAt(x, y, z).apply { if (type != Material.CHEST) type = Material.CHEST }

    private fun inventoryAt(x: Int, y: Int, z: Int): Inventory =
        (world.getBlockAt(x, y, z).state as Chest).blockInventory

    private fun ItemStack.plainDisplayName(): String? =
        itemMeta?.displayName()?.let { PlainTextComponentSerializer.plainText().serialize(it) }

    private fun region(): Region =
        Region(Location(world, 0.0, 0.0, 0.0), Location(world, 2.0, 0.0, 2.0))
}
