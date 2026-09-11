package dev.cypdashuhn.uidesigner.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

val DesignJson =
    Json {
        prettyPrint = true
        encodeDefaults = false
    }

data class BlockPos(
    val x: Int,
    val y: Int,
    val z: Int
) : Comparable<BlockPos> {
    override fun compareTo(other: BlockPos): Int {
        val byX = x.compareTo(other.x)
        if (byX != 0) return byX
        val byY = y.compareTo(other.y)
        if (byY != 0) return byY
        return z.compareTo(other.z)
    }
}

@Serializable
data class UiChest(
    val name: String? = null,
    val rows: Int,
    val content: List<UiRow>,
    @Transient val position: BlockPos? = null,
)

@Serializable
data class UiRow(
    val row: Int,
    val slots: List<UiSlot>
)

@Serializable
data class UiSlot(
    val slot: Int,
    val item: String,
    val name: String? = null
)
