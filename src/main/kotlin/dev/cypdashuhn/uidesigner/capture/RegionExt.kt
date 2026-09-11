package dev.cypdashuhn.uidesigner.capture

import dev.cypdashuhn.uidesigner.model.BlockPos
import dev.rooster.region.Region
import org.bukkit.block.Block

internal fun Region.blockAt(position: BlockPos): Block =
    world.getBlockAt(position.x, position.y, position.z)
