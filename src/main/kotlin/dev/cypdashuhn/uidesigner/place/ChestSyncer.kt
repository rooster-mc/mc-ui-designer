package dev.cypdashuhn.uidesigner.place

import dev.cypdashuhn.uidesigner.export.ChestStatus
import dev.cypdashuhn.uidesigner.export.ReconcileReport
import dev.cypdashuhn.uidesigner.export.UiChest
import dev.cypdashuhn.uidesigner.export.UiSlot
import dev.cypdashuhn.uidesigner.export.requiredPosition
import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.rooster.region.Region
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.inventory.ItemStack

object ChestSyncer {
    private const val SLOTS_PER_ROW = 9

    data class SyncResult(
        val applied: Int,
        val contentsSkipped: Int,
    )

    fun apply(
        region: Region,
        report: ReconcileReport,
        materialOf: (String) -> Material? = { Material.matchMaterial(it) },
    ): SyncResult {
        var applied = 0
        var skipped = 0
        report.matched.forEach { status ->
            val (file, block) =
                when (status) {
                    is ChestStatus.InSync ->
                        status.file to
                            region.blockAt(status.world.requiredPosition())
                    is ChestStatus.Updated ->
                        status.file to
                            region.blockAt(status.world.requiredPosition())
                    else -> return@forEach
                }
            ChestNamer.setName(block, file.name)
            val skipContents = status is ChestStatus.Updated && status.drift.rowsDiffer
            if (skipContents) {
                skipped++
            } else {
                writeContents(block, file, materialOf)
            }
            applied++
        }
        return SyncResult(applied, skipped)
    }

    private fun writeContents(block: Block, file: UiChest, materialOf: (String) -> Material?) {
        val chest = block.state as? Chest ?: return
        val inventory = chest.inventory
        if (inventory.size != file.rows * SLOTS_PER_ROW) return
        val items = arrayOfNulls<ItemStack>(inventory.size)
        file.content.forEach { row ->
            row.slots.forEach { slot ->
                val index = (row.row - 1) * SLOTS_PER_ROW + (slot.slot - 1)
                if (index in items.indices) items[index] = itemOf(slot, materialOf)
            }
        }
        inventory.contents = items
    }

    private fun itemOf(slot: UiSlot, materialOf: (String) -> Material?): ItemStack? {
        val material = materialOf(slot.item) ?: return null
        val item = ItemStack(material)
        val name = slot.name?.takeIf { it.isNotBlank() } ?: return item
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text(name))
        item.itemMeta = meta
        return item
    }
}
