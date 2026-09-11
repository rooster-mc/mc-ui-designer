package dev.cypdashuhn.uidesigner.capture

import dev.rooster.region.Region
import dev.rooster.region.worldedit.toRegion
import dev.rooster.region.worldedit.worldEditSelection
import org.bukkit.entity.Player

object FaweSelectionSource : SelectionSource {
    override fun selectionOf(player: Player): Region? {
        val selection = player.worldEditSelection() ?: return null
        // The session's selection world survives a teleport, so a stale selection from another
        // world must not be reinterpreted at the player's current-world coordinates.
        if (selection.world?.name != player.world.name) return null
        return selection.toRegion(player.world)
    }
}
