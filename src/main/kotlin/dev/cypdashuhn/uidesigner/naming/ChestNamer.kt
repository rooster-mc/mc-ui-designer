package dev.cypdashuhn.uidesigner.naming

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.block.DoubleChest
import org.bukkit.inventory.InventoryHolder

object ChestNamer {
    fun isChest(block: Block): Boolean =
        block.type == Material.CHEST ||
            block.type == Material.TRAPPED_CHEST ||
            Tag.COPPER_CHESTS.isTagged(block.type)

    fun nameOf(block: Block): String? = nameOf(chestsOf(block))

    fun setName(block: Block, name: String) {
        if (name.isBlank()) {
            clear(block)
        } else {
            setName(chestsOf(block), name)
        }
    }

    fun clear(block: Block) {
        clear(chestsOf(block))
    }

    internal fun nameOf(chests: List<Chest>): String? = chests.firstNotNullOfOrNull(::readName)

    internal fun setName(chests: List<Chest>, name: String) {
        val component = Component.text(name)
        chests.forEach { writeName(it, component) }
    }

    internal fun clear(chests: List<Chest>) {
        chests.forEach { writeName(it, null) }
    }

    internal fun chestsOf(block: Block): List<Chest> {
        val chest = block.state as? Chest ?: return emptyList()
        return chestsOf(chest, chest.inventory.holder)
    }

    internal fun chestsOf(chest: Chest, holder: InventoryHolder?): List<Chest> {
        val doubleChest = holder as? DoubleChest ?: return listOf(chest)
        val left = doubleChest.leftSide as? Chest
        val right = doubleChest.rightSide as? Chest
        return if (left != null && right != null) listOf(left, right) else listOf(chest)
    }

    private fun readName(chest: Chest): String? {
        val name = chest.customName() ?: return null
        return PlainTextComponentSerializer.plainText().serialize(name).ifBlank { null }
    }

    private fun writeName(chest: Chest, name: Component?) {
        chest.customName(name)
        chest.update(true)
    }
}
