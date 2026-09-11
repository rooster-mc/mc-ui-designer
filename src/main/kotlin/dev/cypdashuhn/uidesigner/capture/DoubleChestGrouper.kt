package dev.cypdashuhn.uidesigner.capture

import dev.cypdashuhn.uidesigner.model.BlockPos
import dev.cypdashuhn.uidesigner.model.UiChest
import dev.cypdashuhn.uidesigner.model.UiRow
import dev.cypdashuhn.uidesigner.model.UiSlot
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Chest
import org.bukkit.block.DoubleChest
import org.bukkit.inventory.ItemStack
import org.bukkit.block.data.type.Chest as ChestData

object DoubleChestGrouper {
    private const val SLOTS_PER_ROW = 9

    fun group(region: Region, contents: List<ChestContent>): List<UiChest> {
        val byPosition = contents.associateBy { it.position }
        val consumed = mutableSetOf<BlockPos>()
        val chests = mutableListOf<UiChest>()
        for (content in contents) {
            if (content.position in consumed) continue
            val match = match(region, content.position, byPosition, consumed)
            if (match != null) {
                consumed += match.positions
                chests += uiChest(match.items, match.positions.min())
            } else {
                consumed += content.position
                chests += uiChest(content.items, content.position)
            }
        }
        return chests
    }

    private fun uiChest(items: List<ItemStack?>, position: BlockPos): UiChest =
        UiChest(rows = rowCount(items), content = rows(items), position = position)

    private fun match(
        region: Region,
        position: BlockPos,
        byPosition: Map<BlockPos, ChestContent>,
        consumed: Set<BlockPos>,
    ): DoubleMatch? {
        val block = region.world.getBlockAt(position.x, position.y, position.z)
        val chest = block.state as? Chest ?: return null
        val holder = chest.inventory.holder as? DoubleChest
        if (holder != null) {
            val positions = holder.halvesIn(byPosition, consumed)
            if (positions != null) return DoubleMatch(positions, holder.inventory.contents.toList())
        }
        val data = block.blockData as? ChestData ?: return null
        return geometryMatch(region, block, data, byPosition, consumed)
    }

    private fun geometryMatch(
        region: Region,
        block: Block,
        data: ChestData,
        byPosition: Map<BlockPos, ChestContent>,
        consumed: Set<BlockPos>,
    ): DoubleMatch? {
        val offset = partnerOffset(data.type, data.facing) ?: return null
        val here = BlockPos(block.x, block.y, block.z)
        val partner = BlockPos(block.x + offset.first, block.y, block.z + offset.second)
        if (partner in consumed || byPosition[partner] == null) return null
        if (!isComplementaryHalf(region, partner, data)) return null
        return DoubleMatch(listOf(here, partner), orderedItems(here, partner, byPosition))
    }

    private fun isComplementaryHalf(region: Region, partner: BlockPos, data: ChestData): Boolean {
        val partnerData =
            region.world.getBlockAt(partner.x, partner.y, partner.z).blockData as? ChestData
                ?: return false
        return partnerData.facing == data.facing && partnerData.type == opposite(data.type)
    }

    private fun opposite(type: ChestData.Type): ChestData.Type? =
        when (type) {
            ChestData.Type.LEFT -> ChestData.Type.RIGHT
            ChestData.Type.RIGHT -> ChestData.Type.LEFT
            ChestData.Type.SINGLE -> null
        }

    private fun orderedItems(
        here: BlockPos,
        partner: BlockPos,
        byPosition: Map<BlockPos, ChestContent>,
    ): List<ItemStack?> = listOf(here, partner).sorted().flatMap { byPosition.getValue(it).items }

    private fun DoubleChest.halvesIn(
        byPosition: Map<BlockPos, ChestContent>,
        consumed: Set<BlockPos>,
    ): List<BlockPos>? =
        listOfNotNull(leftSide as? Chest, rightSide as? Chest)
            .map { BlockPos(it.x, it.y, it.z) }
            .filter { it in byPosition && it !in consumed }
            .takeIf { it.size == 2 }

    private fun partnerOffset(type: ChestData.Type, facing: BlockFace): Pair<Int, Int>? =
        when (type) {
            ChestData.Type.LEFT ->
                when (facing) {
                    BlockFace.NORTH -> 1 to 0
                    BlockFace.SOUTH -> -1 to 0
                    BlockFace.EAST -> 0 to 1
                    BlockFace.WEST -> 0 to -1
                    else -> null
                }
            ChestData.Type.RIGHT ->
                when (facing) {
                    BlockFace.NORTH -> -1 to 0
                    BlockFace.SOUTH -> 1 to 0
                    BlockFace.EAST -> 0 to -1
                    BlockFace.WEST -> 0 to 1
                    else -> null
                }
            ChestData.Type.SINGLE -> null
        }

    private fun rowCount(items: List<ItemStack?>): Int {
        require(items.isNotEmpty() && items.size % SLOTS_PER_ROW == 0) {
            "Chest inventory size ${items.size} is not a positive multiple of $SLOTS_PER_ROW"
        }
        return items.size / SLOTS_PER_ROW
    }

    private fun rows(items: List<ItemStack?>): List<UiRow> =
        items
            .withIndex()
            .mapNotNull { (index, stack) ->
                stack?.takeUnless { it.isEmpty }?.let { index to it }
            }.groupBy { (index, _) -> index / SLOTS_PER_ROW + 1 }
            .map { (row, entries) ->
                UiRow(
                    row = row,
                    slots =
                        entries.map { (index, stack) ->
                            UiSlot(
                                slot = index % SLOTS_PER_ROW + 1,
                                item = stack.type.key.toString(),
                                name = stack.plainDisplayName(),
                            )
                        },
                )
            }

    private fun ItemStack.plainDisplayName(): String? =
        itemMeta
            ?.displayName()
            ?.let { PlainTextComponentSerializer.plainText().serialize(it) }
            ?.ifBlank { null }

    private data class DoubleMatch(
        val positions: List<BlockPos>,
        val items: List<ItemStack?>,
    )
}
