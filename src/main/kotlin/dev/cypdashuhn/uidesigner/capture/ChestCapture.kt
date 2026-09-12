package dev.cypdashuhn.uidesigner.capture

import dev.rooster.region.Region
import org.bukkit.entity.Player

data class CapturedSelection(
    val region: Region,
    val contents: List<ChestContent>
)

object ChestCapture {
    fun capture(selectionOf: (Player) -> Region?, player: Player): CapturedSelection? =
        selectionOf(player)?.let { CapturedSelection(it, ChestScanner.scan(it)) }
}
