package dev.cypdashuhn.uidesigner.capture

import org.bukkit.entity.Player

interface SelectionSource {
    fun selectionOf(player: Player): Region?
}
