package dev.cypdashuhn.uidesigner.capture

import dev.cypdashuhn.uidesigner.model.BlockPos
import org.bukkit.Material
import org.bukkit.block.Chest

object ChestScanner {
    private val CHEST_MATERIALS = setOf(Material.CHEST, Material.TRAPPED_CHEST)

    fun scan(region: Region): List<ChestContent> {
        val found = mutableListOf<ChestContent>()
        for (x in region.min.x..region.max.x) {
            for (y in region.min.y..region.max.y) {
                for (z in region.min.z..region.max.z) {
                    if (!region.world.isChunkLoaded(x shr 4, z shr 4)) continue
                    val block = region.world.getBlockAt(x, y, z)
                    if (block.type !in CHEST_MATERIALS) continue
                    val chest = block.state as? Chest ?: continue
                    found +=
                        ChestContent(
                            position = BlockPos(block.x, block.y, block.z),
                            items = chest.blockInventory.contents.map { it?.clone() },
                        )
                }
            }
        }
        return found
    }
}
