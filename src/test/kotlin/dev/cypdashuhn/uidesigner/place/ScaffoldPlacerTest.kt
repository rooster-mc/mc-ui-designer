package dev.cypdashuhn.uidesigner.place

import dev.cypdashuhn.uidesigner.export.UiChest
import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.rooster.region.BlockPos
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.world.WorldMock
import org.bukkit.block.data.type.Chest as ChestData

class ScaffoldPlacerTest {
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
    fun `single chests line up across the view and face the player`() {
        val result = place(listOf(chest("Shop", 3), chest("Mine", 3)))

        assertEquals(PlacementResult.Placed(2), result)
        assertChest(BlockPos(0, 64, 0), "Shop", BlockFace.SOUTH, ChestData.Type.SINGLE)
        assertChest(BlockPos(1, 64, 0), "Mine", BlockFace.SOUTH, ChestData.Type.SINGLE)
    }

    @Test
    fun `a six-row chest becomes a linked right-left pair`() {
        val result = place(listOf(chest("Shop", 6)))

        assertEquals(PlacementResult.Placed(1), result)
        assertChest(BlockPos(0, 64, 0), "Shop", BlockFace.SOUTH, ChestData.Type.RIGHT)
        assertChest(BlockPos(1, 64, 0), null, BlockFace.SOUTH, ChestData.Type.LEFT)
    }

    @Test
    fun `the next chest follows a double pair`() {
        val result = place(listOf(chest("Shop", 6), chest("Mine", 3)))

        assertEquals(PlacementResult.Placed(2), result)
        assertChest(BlockPos(0, 64, 0), "Shop", BlockFace.SOUTH, ChestData.Type.RIGHT)
        assertChest(BlockPos(1, 64, 0), null, BlockFace.SOUTH, ChestData.Type.LEFT)
        assertChest(BlockPos(2, 64, 0), "Mine", BlockFace.SOUTH, ChestData.Type.SINGLE)
    }

    @Test
    fun `an east-facing player gets a row running south`() {
        val result =
            ScaffoldPlacer.place(
                world,
                listOf(chest("Shop", 3), chest("Mine", 3)),
                ANCHOR,
                BlockFace.EAST,
            )

        assertEquals(PlacementResult.Placed(2), result)
        assertChest(BlockPos(0, 64, 0), "Shop", BlockFace.WEST, ChestData.Type.SINGLE)
        assertChest(BlockPos(0, 64, 1), "Mine", BlockFace.WEST, ChestData.Type.SINGLE)
    }

    @Test
    fun `rows other than six stay a single chest`() {
        val result = place(listOf(chest("Shop", 4)))

        assertEquals(PlacementResult.Placed(1), result)
        assertChest(BlockPos(0, 64, 0), "Shop", BlockFace.SOUTH, ChestData.Type.SINGLE)
    }

    @Test
    fun `obstruction places nothing and reports the first blocked position`() {
        blockAt(1, 64, 0).type = Material.STONE

        val result = place(listOf(chest("Shop", 3), chest("Mine", 3)))

        assertEquals(PlacementResult.Obstructed(1, BlockPos(1, 64, 0), false), result)
        assertEquals(Material.AIR, blockAt(0, 64, 0).type)
        assertNull(ChestNamer.nameOf(blockAt(0, 64, 0)))
        assertNull(ChestNamer.nameOf(blockAt(1, 64, 0)))
    }

    @Test
    fun `obstruction counts every blocked target and reports the first`() {
        blockAt(0, 64, 0).type = Material.STONE
        blockAt(2, 64, 0).type = Material.STONE

        val result =
            place(listOf(chest("Shop", 3), chest("Mine", 3), chest("Vault", 3)))

        assertEquals(PlacementResult.Obstructed(2, BlockPos(0, 64, 0), false), result)
        assertEquals(Material.AIR, blockAt(1, 64, 0).type)
    }

    @Test
    fun `a player-occupied target aborts the whole placement`() {
        val occupied = { block: Block -> block.x == 1 && block.y == 64 && block.z == 0 }

        val result =
            ScaffoldPlacer.place(
                world,
                listOf(chest("Shop", 3), chest("Mine", 3)),
                ANCHOR,
                BlockFace.NORTH,
                occupied,
            )

        assertEquals(PlacementResult.Obstructed(1, BlockPos(1, 64, 0), true), result)
        assertEquals(Material.AIR, blockAt(0, 64, 0).type)
    }

    @Test
    fun `a replaceable target is overwritten`() {
        blockAt(0, 64, 0).type = Material.SHORT_GRASS

        val result = place(listOf(chest("Shop", 3)))

        assertEquals(PlacementResult.Placed(1), result)
        assertChest(BlockPos(0, 64, 0), "Shop", BlockFace.SOUTH, ChestData.Type.SINGLE)
    }

    private fun place(chests: List<UiChest>): PlacementResult =
        ScaffoldPlacer.place(world, chests, ANCHOR, BlockFace.NORTH)

    private fun chest(name: String, rows: Int): UiChest =
        UiChest(name = name, rows = rows, content = emptyList())

    private fun assertChest(
        position: BlockPos,
        name: String?,
        facing: BlockFace,
        type: ChestData.Type,
    ) {
        val block = blockAt(position.x, position.y, position.z)
        assertEquals(Material.CHEST, block.type)
        val data = block.blockData as ChestData
        assertEquals(facing, data.facing)
        assertEquals(type, data.type)
        assertEquals(name, ChestNamer.nameOf(block))
    }

    private fun blockAt(x: Int, y: Int, z: Int): Block = world.getBlockAt(x, y, z)

    private companion object {
        val ANCHOR = BlockPos(0, 64, 0)
    }
}
