package dev.cypdashuhn.uidesigner.capture

import dev.rooster.region.Region
import org.bukkit.entity.Player

interface SelectionSource {
    fun selectionOf(player: Player): Region?
}
