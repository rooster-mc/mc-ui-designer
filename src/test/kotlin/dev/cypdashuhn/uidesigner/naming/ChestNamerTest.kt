package dev.cypdashuhn.uidesigner.naming

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.block.DoubleChest
import org.bukkit.inventory.DoubleChestInventory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.world.WorldMock
import java.lang.reflect.Proxy

class ChestNamerTest {
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
    fun `unnamed chest has no name`() {
        assertNull(ChestNamer.nameOf(blockAt(Material.CHEST)))
    }

    @Test
    fun `set name then read it back`() {
        val chest = blockAt(Material.CHEST)

        ChestNamer.setName(chest, "Shop")

        assertEquals("Shop", ChestNamer.nameOf(chest))
    }

    @Test
    fun `clear removes the name`() {
        val chest = blockAt(Material.CHEST)
        ChestNamer.setName(chest, "Shop")

        ChestNamer.clear(chest)

        assertNull(ChestNamer.nameOf(chest))
    }

    @Test
    fun `trapped chest round-trips a name`() {
        val chest = blockAt(Material.TRAPPED_CHEST, x = 1)

        ChestNamer.setName(chest, "Shop")
        assertEquals("Shop", ChestNamer.nameOf(chest))

        ChestNamer.clear(chest)
        assertNull(ChestNamer.nameOf(chest))
    }

    @Test
    fun `a blank custom name reads as no name`() {
        val chest = blockAt(Material.CHEST)

        ChestNamer.setName(chest, "   ")

        assertNull(ChestNamer.nameOf(chest))
    }

    @Test
    fun `chest blocks are chests`() {
        assertTrue(ChestNamer.isChest(blockAt(Material.CHEST)))
        assertTrue(ChestNamer.isChest(blockAt(Material.TRAPPED_CHEST, x = 1)))
    }

    @Test
    fun `stone is not a chest`() {
        val stone = blockAt(Material.STONE)

        assertFalse(ChestNamer.isChest(stone))
        assertNull(ChestNamer.nameOf(stone))
    }

    @Test
    fun `a non-chest block has no chest halves`() {
        assertTrue(ChestNamer.chestsOf(blockAt(Material.STONE)).isEmpty())
    }

    @Test
    fun `a non-double holder falls back to the given chest`() {
        val chest = chestAt(Material.CHEST, 0)

        assertEquals(listOf(chest), ChestNamer.chestsOf(chest, null))
    }

    @Test
    fun `name is read from either half of a double chest`() {
        val left = chestAt(Material.CHEST, 0)
        val right = chestAt(Material.CHEST, 1)
        val halves = ChestNamer.chestsOf(left, doubleChestOf(left, right))

        ChestNamer.setName(listOf(right), "Shop")

        assertEquals("Shop", ChestNamer.nameOf(halves))
    }

    @Test
    fun `setting and clearing a name writes both halves`() {
        val left = chestAt(Material.CHEST, 0)
        val right = chestAt(Material.CHEST, 1)
        val halves = ChestNamer.chestsOf(left, doubleChestOf(left, right))

        ChestNamer.setName(halves, "Shop")
        assertEquals("Shop", ChestNamer.nameOf(listOf(left)))
        assertEquals("Shop", ChestNamer.nameOf(listOf(right)))

        ChestNamer.clear(halves)
        assertNull(ChestNamer.nameOf(listOf(left)))
        assertNull(ChestNamer.nameOf(listOf(right)))
    }

    private fun doubleChestOf(left: Chest, right: Chest): DoubleChest {
        val inventory =
            Proxy.newProxyInstance(
                DoubleChestInventory::class.java.classLoader,
                arrayOf<Class<*>>(DoubleChestInventory::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getLeftSide" -> left.inventory
                    "getRightSide" -> right.inventory
                    "toString" -> "DoubleChestInventory"
                    "hashCode" -> System.identityHashCode(left)
                    else -> null
                }
            } as DoubleChestInventory
        return DoubleChest(inventory)
    }

    private fun chestAt(material: Material, x: Int): Chest = blockAt(material, x).state as Chest

    private fun blockAt(material: Material, x: Int = 0): Block =
        world.getBlockAt(x, 0, 0).apply { type = material }
}
