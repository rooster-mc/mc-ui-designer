package dev.cypdashuhn.uidesigner.capture

import dev.cypdashuhn.uidesigner.model.BlockPos
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.world.WorldMock

class ChestScannerTest {
    private lateinit var server: ServerMock
    private lateinit var world: WorldMock
    private lateinit var player: Player

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        world = server.addSimpleWorld("world")
        world.getChunkAt(0, 0)
        player = server.addPlayer()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `no selection captures nothing`() {
        assertNull(ChestCapture.capture(FakeSelectionSource(null), player))
    }

    @Test
    fun `a selection without chests captures an empty list`() {
        blockAt(Material.STONE, 0, 0, 0)
        blockAt(Material.BARREL, 1, 0, 0)

        assertEquals(
            emptyList<ChestContent>(),
            ChestCapture.capture(FakeSelectionSource(region(0, 0, 0, 1, 0, 0)), player),
        )
    }

    @Test
    fun `several single chests are captured in position order`() {
        blockAt(Material.CHEST, 2, 0, 0)
        blockAt(Material.TRAPPED_CHEST, 0, 0, 2)
        blockAt(Material.CHEST, 0, 2, 0)
        blockAt(Material.CHEST, 0, 0, 0)

        val captured = capture(FakeSelectionSource(region(0, 0, 0, 2, 2, 2)))

        assertEquals(
            listOf(
                BlockPos(0, 0, 0),
                BlockPos(0, 0, 2),
                BlockPos(0, 2, 0),
                BlockPos(2, 0, 0),
            ),
            captured.map { it.position },
        )
    }

    @Test
    fun `only chest blocks are captured`() {
        blockAt(Material.CHEST, 0, 0, 0)
        blockAt(Material.TRAPPED_CHEST, 1, 0, 0)
        blockAt(Material.BARREL, 2, 0, 0)
        blockAt(Material.SHULKER_BOX, 3, 0, 0)
        blockAt(Material.STONE, 4, 0, 0)

        val captured = capture(FakeSelectionSource(region(0, 0, 0, 4, 0, 0)))

        assertEquals(
            listOf(BlockPos(0, 0, 0), BlockPos(1, 0, 0)),
            captured.map { it.position },
        )
    }

    @Test
    fun `item material and custom name are read from the slot they occupy`() {
        val chest = blockAt(Material.CHEST, 0, 0, 0).state as Chest
        chest.blockInventory.setItem(5, namedItem(Material.STONE, "Stone"))

        val content = capture(FakeSelectionSource(region(0, 0, 0, 0, 0, 0))).single()

        assertEquals(27, content.items.size)
        assertNull(content.items[0])
        assertEquals(Material.STONE, content.items[5]?.type)
        assertEquals("Stone", content.items[5]?.plainDisplayName())

        content.items[5]!!.amount = 42
        assertEquals(1, chest.blockInventory.getItem(5)?.amount)
    }

    @Test
    fun `adjacent chests are captured as separate 27-slot entries`() {
        val left = blockAt(Material.CHEST, 0, 0, 0).state as Chest
        val right = blockAt(Material.CHEST, 1, 0, 0).state as Chest
        left.blockInventory.setItem(0, ItemStack(Material.STONE))
        right.blockInventory.setItem(0, ItemStack(Material.DIRT))

        val captured = capture(FakeSelectionSource(region(0, 0, 0, 1, 0, 0)))

        assertEquals(
            listOf(BlockPos(0, 0, 0), BlockPos(1, 0, 0)),
            captured.map { it.position },
        )
        assertEquals(listOf(27, 27), captured.map { it.items.size })
        assertEquals(Material.STONE, captured[0].items[0]?.type)
        assertEquals(Material.DIRT, captured[1].items[0]?.type)
        assertNotSame(captured[0].items[0], captured[1].items[0])
    }

    @Test
    fun `negative coordinates use floor chunk lookup`() {
        world.getChunkAt(-2, 0)
        blockAt(Material.CHEST, -17, 0, 0)

        val captured = capture(FakeSelectionSource(region(-17, 0, 0, -17, 0, 0)))

        assertEquals(listOf(BlockPos(-17, 0, 0)), captured.map { it.position })
    }

    @Test
    fun `chests in unloaded chunks are skipped without error`() {
        blockAt(Material.CHEST, 16, 0, 0)

        assertFalse(world.isChunkLoaded(1, 0))
        assertTrue(capture(FakeSelectionSource(region(16, 0, 0, 16, 0, 0))).isEmpty())
    }

    private fun capture(source: SelectionSource): List<ChestContent> =
        checkNotNull(ChestCapture.capture(source, player))

    private fun namedItem(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(Component.text(name))
        item.itemMeta = meta
        return item
    }

    private fun ItemStack.plainDisplayName(): String? =
        itemMeta?.displayName()?.let { PlainTextComponentSerializer.plainText().serialize(it) }

    private fun region(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int,): Region =
        Region.of(BlockPos(minX, minY, minZ), BlockPos(maxX, maxY, maxZ), world)

    private fun blockAt(material: Material, x: Int, y: Int, z: Int): Block =
        world.getBlockAt(x, y, z).apply { type = material }

    private class FakeSelectionSource(
        private val region: Region?
    ) : SelectionSource {
        override fun selectionOf(player: Player): Region? = region
    }
}
