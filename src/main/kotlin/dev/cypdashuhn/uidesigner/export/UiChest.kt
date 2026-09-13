package dev.cypdashuhn.uidesigner.export

import dev.rooster.region.BlockPos
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

val DesignJson =
    Json {
        prettyPrint = true
        encodeDefaults = false
    }

@Serializable
data class UiChest(
    val name: String,
    val rows: Int,
    val content: List<UiRow>,
    @Transient val position: BlockPos? = null,
)

internal fun UiChest.requiredPosition(): BlockPos =
    requireNotNull(position) { "UiChest.position must be set before export" }

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
