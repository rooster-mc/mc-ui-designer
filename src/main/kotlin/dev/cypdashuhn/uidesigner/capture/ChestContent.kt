package dev.cypdashuhn.uidesigner.capture

import dev.cypdashuhn.uidesigner.model.BlockPos
import org.bukkit.inventory.ItemStack

data class ChestContent(
    val position: BlockPos,
    val items: List<ItemStack?>,
)
