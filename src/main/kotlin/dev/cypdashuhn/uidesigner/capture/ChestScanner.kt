package dev.cypdashuhn.uidesigner.capture

import dev.rooster.region.Region
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Chest

object ChestScanner {
    private val CHEST_MATERIALS = setOf(Material.CHEST, Material.TRAPPED_CHEST)

    fun scan(region: Region): List<ChestContent> =
        region.loadedBlockPositions
            .mapNotNull { position ->
                val block = region.blockAt(position)
                if (!block.type.isChestMaterial()) return@mapNotNull null
                val chest = block.state as? Chest ?: return@mapNotNull null
                ChestContent(
                    position = position,
                    items = chest.blockInventory.contents.map { it?.clone() },
                )
            }.toList()

    private fun Material.isChestMaterial(): Boolean =
        this in CHEST_MATERIALS || Tag.COPPER_CHESTS.isTagged(this)
}
