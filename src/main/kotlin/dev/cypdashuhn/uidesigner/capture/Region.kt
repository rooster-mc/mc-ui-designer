package dev.cypdashuhn.uidesigner.capture

import dev.cypdashuhn.uidesigner.model.BlockPos
import org.bukkit.World

class Region private constructor(
    val min: BlockPos,
    val max: BlockPos,
    val world: World,
) {
    companion object {
        fun of(cornerA: BlockPos, cornerB: BlockPos, world: World): Region {
            val min =
                BlockPos(
                    minOf(cornerA.x, cornerB.x),
                    minOf(cornerA.y, cornerB.y),
                    minOf(cornerA.z, cornerB.z),
                )
            val max =
                BlockPos(
                    maxOf(cornerA.x, cornerB.x),
                    maxOf(cornerA.y, cornerB.y),
                    maxOf(cornerA.z, cornerB.z),
                )
            return Region(min, max, world)
        }
    }
}
