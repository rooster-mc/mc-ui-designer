package dev.cypdashuhn.uidesigner.capture

import org.bukkit.entity.Player

data class CapturedSelection(
    val region: Region,
    val contents: List<ChestContent>
)

object ChestCapture {
    fun capture(source: SelectionSource, player: Player): CapturedSelection? =
        source.selectionOf(player)?.let { CapturedSelection(it, ChestScanner.scan(it)) }
}
