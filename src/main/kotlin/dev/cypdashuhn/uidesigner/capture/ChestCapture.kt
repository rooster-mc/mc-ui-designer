package dev.cypdashuhn.uidesigner.capture

import org.bukkit.entity.Player

object ChestCapture {
    fun capture(source: SelectionSource, player: Player): List<ChestContent>? =
        source.selectionOf(player)?.let(ChestScanner::scan)
}
