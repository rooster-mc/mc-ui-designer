package dev.cypdashuhn.uidesigner.capture

import com.sk89q.worldedit.IncompleteRegionException
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import dev.cypdashuhn.uidesigner.model.BlockPos
import org.bukkit.entity.Player

object FaweSelectionSource : SelectionSource {
    override fun selectionOf(player: Player): Region? {
        val session =
            WorldEdit
                .getInstance()
                .sessionManager
                .getIfPresent(BukkitAdapter.adapt(player))
                ?: return null
        val world = BukkitAdapter.adapt(player.world)
        if (!session.isSelectionDefined(world)) return null
        val selection =
            try {
                session.getSelection(world)
            } catch (_: IncompleteRegionException) {
                return null
            }
        return Region.of(
            BlockPos(
                selection.minimumPoint.x(),
                selection.minimumPoint.y(),
                selection.minimumPoint.z(),
            ),
            BlockPos(
                selection.maximumPoint.x(),
                selection.maximumPoint.y(),
                selection.maximumPoint.z(),
            ),
            BukkitAdapter.adapt(selection.world),
        )
    }
}
