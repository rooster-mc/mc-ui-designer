package dev.cypdashuhn.uidesigner.capture

import dev.rooster.region.Region
import dev.rooster.region.worldedit.toRegion
import dev.rooster.region.worldedit.worldEditSelection
import org.bukkit.entity.Player

object FaweSelectionSource : SelectionSource {
    override fun selectionOf(player: Player): Region? {
        val selection = player.worldEditSelection() ?: return null
        return selection.toRegion(player.world)
    }
}
