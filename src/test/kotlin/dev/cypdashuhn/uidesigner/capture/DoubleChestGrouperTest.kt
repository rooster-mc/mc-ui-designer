package dev.cypdashuhn.uidesigner.capture

import dev.cypdashuhn.uidesigner.model.BlockPos
import dev.cypdashuhn.uidesigner.model.UiChest
import dev.cypdashuhn.uidesigner.model.UiRow
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Chest
import org.bukkit.block.DoubleChest
import org.bukkit.inventory.DoubleChestInventory
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.block.data.ChestDataMock
import org.mockbukkit.mockbukkit.block.state.ChestStateMock
import org.mockbukkit.mockbukkit.inventory.ChestInventoryMock
import org.mockbukkit.mockbukkit.world.WorldMock
import java.lang.reflect.Proxy
import org.bukkit.block.data.type.Chest as ChestData

class DoubleChestGrouperTest {
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
    fun `a single chest yields one three-row entry at its own position`() {
        val chest = plainChest(0, 0)
        chest.inventory.setItem(0, ItemStack(Material.STONE))
        chest.inventory.setItem(9, namedItem(Material.DIRT, "Dirt"))

        val result = DoubleChestGrouper.group(region(0, 0, 0, 0), listOf(content(chest)))

        assertEquals(1, result.size)
        val ui = result.single()
        assertEquals(3, ui.rows)
        assertNull(ui.name)
        assertEquals(BlockPos(0, 0, 0), ui.position)
        assertEquals(
            mapOf((1 to 1) to "minecraft:stone", (2 to 1) to "minecraft:dirt"),
            ui.content.slotItems(),
        )
        assertEquals(
            "Dirt",
            ui.content
                .single { it.row == 2 }
                .slots
                .single()
                .name
        )
    }

    @Test
    fun `adjacent chests without a double holder stay separate singles`() {
        val left = plainChest(0, 0)
        val right = plainChest(1, 0)
        left.inventory.setItem(0, ItemStack(Material.STONE))
        right.inventory.setItem(0, ItemStack(Material.DIRT))

        val result =
            DoubleChestGrouper.group(region(0, 0, 1, 0), listOf(content(left), content(right)))

        assertEquals(2, result.size)
        assertEquals(listOf(BlockPos(0, 0, 0), BlockPos(1, 0, 0)), result.map { it.position })
        assertEquals(listOf(3, 3), result.map { it.rows })
        assertEquals(listOf("minecraft:stone", "minecraft:dirt"), result.map { it.firstItem() })
    }

    @Test
    fun `a single chest is not re-merged by a mismatched adjacent half`() {
        val single = plainChest(0, 0)
        val mismatched = geometryChest(1, 0, BlockFace.SOUTH, ChestData.Type.LEFT)
        single.inventory.setItem(0, ItemStack(Material.STONE))
        mismatched.inventory.setItem(0, ItemStack(Material.DIRT))

        val result =
            DoubleChestGrouper.group(
                region(0, 0, 1, 0),
                listOf(content(single), content(mismatched)),
            )

        assertEquals(2, result.size)
        assertEquals(listOf(BlockPos(0, 0, 0), BlockPos(1, 0, 0)), result.map { it.position })
        assertEquals(listOf(3, 3), result.map { it.rows })
    }

    @Test
    fun `a double chest with both halves present yields one six-row entry`() {
        val items = filledItems(54)
        items[8] = namedItem(Material.DIRT, "Dirt")
        items[9] = ItemStack(Material.GOLD_INGOT)
        items[53] = ItemStack(Material.DIAMOND)
        val left = fakeChest(0, 0)
        val right = fakeChest(1, 0)
        installDouble(left, right, items)

        val result =
            DoubleChestGrouper.group(
                region(0, 0, 1, 0),
                listOf(content(left, items.take(27)), content(right, items.drop(27))),
            )

        assertEquals(1, result.size)
        val ui = result.single()
        assertEquals(6, ui.rows)
        assertEquals(BlockPos(0, 0, 0), ui.position)

        val slots = ui.content.slotItems()
        assertEquals((1..6).flatMap { row -> (1..9).map { row to it } }.toSet(), slots.keys)
        assertEquals("minecraft:stone", slots[1 to 1])
        assertEquals("minecraft:dirt", slots[1 to 9])
        assertEquals("minecraft:gold_ingot", slots[2 to 1])
        assertEquals("minecraft:diamond", slots[6 to 9])
    }

    @Test
    fun `a sparse double chest omits empty slots around the half boundary`() {
        val items = filledItems(54)
        items[26] = null
        items[27] = null
        val left = fakeChest(0, 0)
        val right = fakeChest(1, 0)
        installDouble(left, right, items)

        val result =
            DoubleChestGrouper.group(
                region(0, 0, 1, 0),
                listOf(content(left, items.take(27)), content(right, items.drop(27))),
            )

        val slots = result.single().content.slotItems()
        assertEquals(52, slots.size)
        assertNull(slots[3 to 9])
        assertNull(slots[4 to 1])
        assertEquals("minecraft:stone", slots[3 to 8])
        assertEquals("minecraft:stone", slots[4 to 2])
    }

    @Test
    fun `a double chest is emitted once with the lower half as canonical position`() {
        val result = groupDouble(reversed = false)

        assertEquals(1, result.size)
        assertEquals(BlockPos(0, 0, 0), result.single().position)
        assertEquals(6, result.single().rows)
    }

    @Test
    fun `a double chest keeps the lower half as canonical position when reversed`() {
        val result = groupDouble(reversed = true)

        assertEquals(1, result.size)
        assertEquals(BlockPos(0, 0, 0), result.single().position)
        assertEquals(6, result.single().rows)
    }

    @Test
    fun `a north-facing double chest merges into one entry`() =
        assertOrientationMerges(BlockFace.NORTH, 1, 0)

    @Test
    fun `a south-facing double chest merges into one entry`() =
        assertOrientationMerges(BlockFace.SOUTH, -1, 0)

    @Test
    fun `an east-facing double chest merges into one entry`() =
        assertOrientationMerges(BlockFace.EAST, 0, 1)

    @Test
    fun `a west-facing double chest merges into one entry`() =
        assertOrientationMerges(BlockFace.WEST, 0, -1)

    @Test
    fun `a south-facing double chest merges when the right half is first`() =
        assertOrientationMerges(BlockFace.SOUTH, -1, 0, rightFirst = true)

    @Test
    fun `a west-facing double chest merges when the right half is first`() =
        assertOrientationMerges(BlockFace.WEST, 0, -1, rightFirst = true)

    @Test
    fun `the geometry fallback combines both halves' captured slots`() {
        val left = geometryChest(0, 0, BlockFace.NORTH, ChestData.Type.LEFT)
        val right = geometryChest(1, 0, BlockFace.NORTH, ChestData.Type.RIGHT)
        val leftItems = MutableList<ItemStack?>(27) { null }
        leftItems[0] = ItemStack(Material.STONE)
        leftItems[26] = ItemStack(Material.DIAMOND)
        val rightItems = MutableList<ItemStack?>(27) { null }
        rightItems[0] = ItemStack(Material.GOLD_INGOT)
        rightItems[26] = ItemStack(Material.EMERALD)

        val result =
            DoubleChestGrouper.group(
                region(0, 0, 1, 0),
                listOf(content(left, leftItems), content(right, rightItems)),
            )

        assertEquals(1, result.size)
        val ui = result.single()
        assertEquals(6, ui.rows)
        val slots = ui.content.slotItems()
        assertEquals(4, slots.size)
        assertEquals("minecraft:stone", slots[1 to 1])
        assertEquals("minecraft:diamond", slots[3 to 9])
        assertEquals("minecraft:gold_ingot", slots[4 to 1])
        assertEquals("minecraft:emerald", slots[6 to 9])
    }

    @Test
    fun `only one half selected is treated as a single chest`() {
        val items = filledItems(54)
        val left = fakeChest(0, 0)
        val right = fakeChest(1, 0)
        installDouble(left, right, items)
        val half = MutableList<ItemStack?>(27) { null }
        half[0] = ItemStack(Material.STONE)
        half[8] = ItemStack(Material.DIRT)

        val result =
            DoubleChestGrouper.group(region(0, 0, 1, 0), listOf(content(left, half)))

        assertEquals(1, result.size)
        val ui = result.single()
        assertEquals(3, ui.rows)
        assertEquals(BlockPos(0, 0, 0), ui.position)
        assertEquals(
            mapOf((1 to 1) to "minecraft:stone", (1 to 9) to "minecraft:dirt"),
            ui.content.slotItems(),
        )
    }

    @Test
    fun `a double chest and an unrelated single chest produce two entries`() {
        val items = filledItems(54)
        val left = fakeChest(0, 0)
        val right = fakeChest(1, 0)
        installDouble(left, right, items)
        val single = plainChest(5, 0)
        single.inventory.setItem(0, ItemStack(Material.STONE))

        val result =
            DoubleChestGrouper.group(
                region(0, 0, 5, 0),
                listOf(
                    content(left, items.take(27)),
                    content(right, items.drop(27)),
                    content(single),
                ),
            )

        assertEquals(2, result.size)
        assertEquals(listOf(BlockPos(0, 0, 0), BlockPos(5, 0, 0)), result.map { it.position })
        assertEquals(listOf(6, 3), result.map { it.rows })
        assertEquals(54, result[0].content.sumOf { it.slots.size })
        assertEquals(1, result[1].content.sumOf { it.slots.size })
    }

    private fun groupDouble(reversed: Boolean) =
        run {
            val items = filledItems(54)
            val left = fakeChest(0, 0)
            val right = fakeChest(1, 0)
            installDouble(left, right, items)
            val contents = listOf(content(left, items.take(27)), content(right, items.drop(27)))
            DoubleChestGrouper.group(
                region(0, 0, 1, 0),
                if (reversed) contents.reversed() else contents
            )
        }

    private fun assertOrientationMerges(
        facing: BlockFace,
        dx: Int,
        dz: Int,
        rightFirst: Boolean = false,
    ) {
        val items = filledItems(54)
        val left = geometryChest(0, 0, facing, ChestData.Type.LEFT)
        val right = geometryChest(dx, dz, facing, ChestData.Type.RIGHT)
        val leftContent = content(left, items.take(27))
        val rightContent = content(right, items.drop(27))
        val contents =
            if (rightFirst) listOf(rightContent, leftContent) else listOf(leftContent, rightContent)

        val result =
            DoubleChestGrouper.group(
                region(minOf(0, dx), minOf(0, dz), maxOf(0, dx), maxOf(0, dz)),
                contents,
            )

        assertEquals(1, result.size)
        val ui = result.single()
        assertEquals(6, ui.rows)
        assertEquals(54, ui.content.sumOf { it.slots.size })
        assertEquals(BlockPos(minOf(0, dx), 0, minOf(0, dz)), ui.position)
    }

    private fun installDouble(
        left: FakeChestState,
        right: FakeChestState,
        items: Array<ItemStack?>,
    ): DoubleChest {
        val inventory =
            Proxy.newProxyInstance(
                DoubleChestInventory::class.java.classLoader,
                arrayOf<Class<*>>(DoubleChestInventory::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getLeftSide" -> ChestInventoryMock(left, 27)
                    "getRightSide" -> ChestInventoryMock(right, 27)
                    "getContents" -> items
                    "getSize" -> 54
                    "toString" -> "DoubleChestInventory"
                    "hashCode" -> System.identityHashCode(left)
                    else -> null
                }
            } as DoubleChestInventory
        val holder = DoubleChest(inventory)
        val shared = ChestInventoryMock(holder, 54)
        shared.contents = items
        left.provided = shared
        right.provided = shared
        return holder
    }

    private fun geometryChest(x: Int, z: Int, facing: BlockFace, type: ChestData.Type,): Chest {
        val chest = plainChest(x, z)
        world.getBlockAt(x, 0, z).blockData =
            ChestDataMock(Material.CHEST).apply {
                this.type = type
                this.facing = facing
            }
        return chest
    }

    private fun plainChest(x: Int, z: Int): Chest =
        world.getBlockAt(x, 0, z).apply { type = Material.CHEST }.state as Chest

    private fun fakeChest(x: Int, z: Int): FakeChestState {
        val block = world.getBlockAt(x, 0, z).apply { type = Material.CHEST }
        val state = FakeChestState(block)
        block.setState(state)
        return state
    }

    private fun content(chest: Chest): ChestContent =
        ChestContent(BlockPos(chest.x, chest.y, chest.z), chest.blockInventory.contents.toList())

    private fun content(chest: Chest, items: List<ItemStack?>): ChestContent =
        ChestContent(BlockPos(chest.x, chest.y, chest.z), items)

    private fun namedItem(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(Component.text(name))
        item.itemMeta = meta
        return item
    }

    private fun filledItems(count: Int): Array<ItemStack?> =
        Array(count) { ItemStack(Material.STONE) }

    private fun UiChest.firstItem(): String? =
        content
            .firstOrNull()
            ?.slots
            ?.firstOrNull()
            ?.item

    private fun List<UiRow>.slotItems(): Map<Pair<Int, Int>, String> =
        flatMap { row -> row.slots.map { (row.row to it.slot) to it.item } }.toMap()

    private fun region(minX: Int, minZ: Int, maxX: Int, maxZ: Int): Region =
        Region.of(BlockPos(minX, 0, minZ), BlockPos(maxX, 0, maxZ), world)

    private class FakeChestState(
        block: Block
    ) : ChestStateMock(block) {
        var provided: Inventory? = null

        override fun getInventory(): Inventory = provided ?: super.getInventory()
    }
}
