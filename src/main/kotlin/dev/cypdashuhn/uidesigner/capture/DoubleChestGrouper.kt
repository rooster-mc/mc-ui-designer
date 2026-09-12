package dev.cypdashuhn.uidesigner.capture

import dev.cypdashuhn.uidesigner.export.UiChest
import dev.cypdashuhn.uidesigner.export.UiRow
import dev.cypdashuhn.uidesigner.export.UiSlot
import dev.rooster.region.BlockPos
import dev.rooster.region.Region
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Chest
import org.bukkit.block.DoubleChest
import org.bukkit.inventory.ItemStack
import org.bukkit.block.data.type.Chest as ChestData

data class ClippedHalf(
    val position: BlockPos,
    val partner: BlockPos,
    val partnerInsideSelection: Boolean,
)

data class GroupResult(
    val chests: List<UiChest>,
    val clipped: List<ClippedHalf>,
)

// The DoubleChest inventory holder gives both halves directly, but is unavailable in
// some states (e.g. MockBukkit), so the block-state fallback is needed. A Chest block
// state exposes only type (LEFT/RIGHT) and facing — no direct partner reference — and
// that pair fully determines the neighbour: the partner is always the adjacent block
// on the chest's outer side, i.e. the facing vector rotated ±90°.
object DoubleChestGrouper {
    private const val SLOTS_PER_ROW = 9

    fun group(region: Region, contents: List<ChestContent>): GroupResult {
        val byPosition = contents.associateBy { it.position }
        val consumed = mutableSetOf<BlockPos>()
        val chests = mutableListOf<UiChest>()
        val clipped = mutableListOf<ClippedHalf>()
        for (content in contents) {
            if (content.position in consumed) continue
            val match = match(region, content.position, byPosition, consumed)
            if (match != null) {
                consumed += match.positions
                chests += uiChest(match.items, match.positions.min())
                continue
            }
            val clippedHalf = findClippedHalf(region, content.position, byPosition)
            if (clippedHalf != null) {
                consumed += content.position
                clipped += clippedHalf
                continue
            }
            consumed += content.position
            chests += uiChest(content.items, content.position)
        }
        return GroupResult(chests, clipped)
    }

    private fun uiChest(items: List<ItemStack?>, position: BlockPos): UiChest =
        UiChest(rows = rowCount(items), content = rows(items), position = position)

    private fun match(
        region: Region,
        position: BlockPos,
        byPosition: Map<BlockPos, ChestContent>,
        consumed: Set<BlockPos>,
    ): DoubleMatch? {
        val block = region.blockAt(position)
        val chest = block.state as? Chest ?: return null
        val holder = chest.inventory.holder as? DoubleChest
        if (holder != null) {
            val positions = holder.halvesIn(byPosition, consumed)
            if (positions != null) return DoubleMatch(positions, holder.inventory.contents.toList())
        }
        val data = block.blockData as? ChestData ?: return null
        return geometryMatch(region, block, data, byPosition, consumed)
    }

    private fun findClippedHalf(
        region: Region,
        position: BlockPos,
        byPosition: Map<BlockPos, ChestContent>,
    ): ClippedHalf? {
        val partner = partnerOf(region, position) ?: return null
        if (partner in byPosition) return null
        return ClippedHalf(position, partner, region.containsBlock(partner))
    }

    private fun partnerOf(region: Region, position: BlockPos): BlockPos? {
        val block = region.blockAt(position)
        val chest = block.state as? Chest ?: return null
        val holder = chest.inventory.holder as? DoubleChest
        if (holder != null) {
            val halves = holder.halfPositions()
            val partner = halves.firstOrNull { it != position }
            if (position in halves && partner != null) return partner
        }
        val data = block.blockData as? ChestData ?: return null
        return geometryPartner(block, data)
    }

    private fun geometryMatch(
        region: Region,
        block: Block,
        data: ChestData,
        byPosition: Map<BlockPos, ChestContent>,
        consumed: Set<BlockPos>,
    ): DoubleMatch? {
        val here = BlockPos(block.x, block.y, block.z)
        val partner = geometryPartner(block, data) ?: return null
        if (partner in consumed || byPosition[partner] == null) return null
        if (!isComplementaryHalf(region, partner, data)) return null
        return DoubleMatch(
            listOf(here, partner),
            orderedItems(data.type, here, partner, byPosition),
        )
    }

    private fun geometryPartner(block: Block, data: ChestData): BlockPos? {
        val offset = partnerOffset(data.type, data.facing) ?: return null
        return BlockPos(block.x + offset.first, block.y, block.z + offset.second)
    }

    private fun isComplementaryHalf(region: Region, partner: BlockPos, data: ChestData): Boolean {
        val partnerData =
            region.blockAt(partner).blockData as? ChestData
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
        type: ChestData.Type,
        here: BlockPos,
        partner: BlockPos,
        byPosition: Map<BlockPos, ChestContent>,
    ): List<ItemStack?> {
        val (right, left) =
            if (type == ChestData.Type.RIGHT) here to partner else partner to here
        return byPosition.getValue(right).items + byPosition.getValue(left).items
    }

    private fun DoubleChest.halvesIn(
        byPosition: Map<BlockPos, ChestContent>,
        consumed: Set<BlockPos>,
    ): List<BlockPos>? =
        halfPositions()
            .filter { it in byPosition && it !in consumed }
            .takeIf { it.size == 2 }

    private fun DoubleChest.halfPositions(): List<BlockPos> =
        listOfNotNull(leftSide as? Chest, rightSide as? Chest)
            .map { BlockPos(it.x, it.y, it.z) }

    private fun partnerOffset(type: ChestData.Type, facing: BlockFace): Pair<Int, Int>? {
        if (type == ChestData.Type.SINGLE) return null
        val facingOffset = facing.modX to facing.modZ
        return if (type == ChestData.Type.LEFT) {
            -facingOffset.second to facingOffset.first
        } else {
            facingOffset.second to -facingOffset.first
        }
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

private fun Region.containsBlock(position: BlockPos): Boolean =
    position.x in minX..maxX && position.y in minY..maxY && position.z in minZ..maxZ
