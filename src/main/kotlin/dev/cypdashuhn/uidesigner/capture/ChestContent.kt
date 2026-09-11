package dev.cypdashuhn.uidesigner.capture

import dev.rooster.region.BlockPos
import org.bukkit.inventory.ItemStack

data class ChestContent(
    val position: BlockPos,
    val items: List<ItemStack?>,
)
