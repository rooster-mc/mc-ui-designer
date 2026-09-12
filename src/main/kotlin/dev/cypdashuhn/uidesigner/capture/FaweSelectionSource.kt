package dev.cypdashuhn.uidesigner.capture

import dev.rooster.region.Region
import dev.rooster.region.worldedit.toRegion
import dev.rooster.region.worldedit.worldEditSelection
import org.bukkit.entity.Player

// TODO: This object has a helper for a two-liner appearing one time. inline it, remove this file
// including the interface, we dont need the generalization here for a module we wont ever change
// up.
object FaweSelectionSource : SelectionSource {
    override fun selectionOf(player: Player): Region? {
        val selection = player.worldEditSelection() ?: return null
        return selection.toRegion(player.world)
    }
}
