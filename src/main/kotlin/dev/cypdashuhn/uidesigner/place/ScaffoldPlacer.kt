package dev.cypdashuhn.uidesigner.place

import dev.cypdashuhn.uidesigner.export.UiChest
import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.rooster.region.BlockPos
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import org.bukkit.block.data.type.Chest as ChestData

sealed interface PlacementResult {
    data class Placed(
        val chests: Int
    ) : PlacementResult

    data class Obstructed(
        val blocked: Int,
        val first: BlockPos,
        val firstIsPlayer: Boolean,
    ) : PlacementResult

    data object NoTarget : PlacementResult
}

private data class Obstruction(
    val position: BlockPos,
    val byPlayer: Boolean,
)

private data class BlockPlan(
    val position: BlockPos,
    val type: ChestData.Type
)

private data class ChestPlan(
    val chest: UiChest,
    val facing: BlockFace,
    val blocks: List<BlockPlan>,
) {
    val start: BlockPlan get() = blocks.first()
}

object ScaffoldPlacer {
    private const val TARGET_REACH = 5
    private const val DOUBLE_ROWS = 6

    fun place(player: Player, chests: List<UiChest>): PlacementResult {
        val anchor = anchorOf(player) ?: return PlacementResult.NoTarget
        return place(player.world, chests, anchor, player.facing) { block ->
            player.boundingBox.overlaps(BoundingBox.of(block))
        }
    }

    fun place(
        world: World,
        chests: List<UiChest>,
        anchor: BlockPos,
        view: BlockFace,
        occupied: (Block) -> Boolean = { false },
    ): PlacementResult {
        val plans = layout(chests, anchor, view)
        val obstructed =
            plans.flatMap { it.blocks }.mapNotNull { block ->
                val target = world.blockAt(block.position)
                when {
                    occupied(target) -> Obstruction(block.position, byPlayer = true)
                    !target.type.isAir && !target.isReplaceable ->
                        Obstruction(block.position, byPlayer = false)
                    else -> null
                }
            }
        if (obstructed.isNotEmpty()) {
            val first = obstructed.first()
            return PlacementResult.Obstructed(obstructed.size, first.position, first.byPlayer)
        }
        plans.forEach { plan -> plan.blocks.forEach { placeEmpty(world, it, plan.facing) } }
        plans.forEach { plan ->
            ChestNamer.setName(world.blockAt(plan.start.position), plan.chest.name)
        }
        return PlacementResult.Placed(plans.size)
    }

    private fun placeEmpty(world: World, block: BlockPlan, facing: BlockFace) {
        val target = world.blockAt(block.position)
        target.type = Material.CHEST
        val state = target.state
        val data = state.blockData as ChestData
        data.facing = facing
        data.type = block.type
        state.blockData = data
        state.update(true)
    }

    private fun layout(chests: List<UiChest>, anchor: BlockPos, view: BlockFace): List<ChestPlan> {
        val facing = view.oppositeFace
        val step = view.rotateYClockwise()
        var offset = 0
        return chests.map { chest ->
            val blocks = mutableListOf<BlockPlan>()
            if (chest.rows == DOUBLE_ROWS) {
                blocks += BlockPlan(anchor.offsetBy(step, offset), ChestData.Type.RIGHT)
                blocks += BlockPlan(anchor.offsetBy(step, offset + 1), ChestData.Type.LEFT)
                offset += 2
            } else {
                blocks += BlockPlan(anchor.offsetBy(step, offset), ChestData.Type.SINGLE)
                offset += 1
            }
            ChestPlan(chest, facing, blocks)
        }
    }

    private fun anchorOf(player: Player): BlockPos? {
        val target = player.getTargetBlockExact(TARGET_REACH)
        if (target != null) return target.toBlockPos()
        return player.location.block
            .getRelative(player.facing)
            .toBlockPos()
    }

    private fun BlockPos.offsetBy(step: BlockFace, offset: Int): BlockPos =
        BlockPos(x + step.modX * offset, y, z + step.modZ * offset)

    private fun BlockFace.rotateYClockwise(): BlockFace =
        when (this) {
            BlockFace.NORTH -> BlockFace.EAST
            BlockFace.EAST -> BlockFace.SOUTH
            BlockFace.SOUTH -> BlockFace.WEST
            BlockFace.WEST -> BlockFace.NORTH
            else -> this
        }

    private fun Block.toBlockPos(): BlockPos = BlockPos(x, y, z)

    private fun World.blockAt(position: BlockPos): Block =
        getBlockAt(position.x, position.y, position.z)
}
