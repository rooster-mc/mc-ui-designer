package dev.cypdashuhn.uidesigner.export

import dev.rooster.region.BlockPos

enum class UnjoinableReason {
    UNNAMED,
    DUPLICATE,
}

data class NormalizedSlot(
    val item: String,
    val name: String?,
)

data class SlotDifference(
    val row: Int,
    val slot: Int,
    val file: NormalizedSlot?,
    val world: NormalizedSlot?,
)

data class ChestDrift(
    val rowsDiffer: Boolean,
    val nameDiffer: Boolean,
    val differences: List<SlotDifference>,
)

sealed interface ChestStatus {
    data class InSync(
        val file: UiChest,
        val world: UiChest,
    ) : ChestStatus

    data class Updated(
        val file: UiChest,
        val world: UiChest,
        val drift: ChestDrift,
    ) : ChestStatus

    data class Missing(
        val file: UiChest,
    ) : ChestStatus

    data class Orphan(
        val world: UiChest,
    ) : ChestStatus

    data class Unjoinable(
        val world: UiChest,
        val reason: UnjoinableReason,
    ) : ChestStatus
}

data class ReconcileReport(
    val statuses: List<ChestStatus>,
) {
    val inSync: List<ChestStatus.InSync> get() = statuses.filterIsInstance<ChestStatus.InSync>()

    val updated: List<ChestStatus.Updated> get() = statuses.filterIsInstance<ChestStatus.Updated>()

    val missing: List<ChestStatus.Missing> get() = statuses.filterIsInstance<ChestStatus.Missing>()

    val orphans: List<ChestStatus.Orphan> get() = statuses.filterIsInstance<ChestStatus.Orphan>()

    val unjoinable: List<ChestStatus.Unjoinable> get() =
        statuses.filterIsInstance<ChestStatus.Unjoinable>()

    val matched: List<ChestStatus> get() =
        statuses.filter { it is ChestStatus.InSync || it is ChestStatus.Updated }

    val isInSync: Boolean get() = statuses.all { it is ChestStatus.InSync }
}

object Reconcile {
    fun reconcile(file: List<UiChest>, world: List<UiChest>): ReconcileReport {
        val nameCounts =
            world
                .filter { it.name.isNotBlank() }
                .groupingBy { normalizeName(it.name) }
                .eachCount()
        val joinable = mutableMapOf<String, UiChest>()
        val unjoinable = mutableSetOf<BlockPos>()
        world.forEach { chest ->
            val key = normalizeName(chest.name)
            when {
                chest.name.isBlank() -> unjoinable += chest.requiredPosition()
                nameCounts.getValue(key) > 1 -> unjoinable += chest.requiredPosition()
                else -> joinable[key] = chest
            }
        }
        val statuses = mutableListOf<ChestStatus>()
        val consumed = mutableSetOf<BlockPos>()
        file.forEach { desired ->
            val worldChest = joinable[normalizeName(desired.name)]
            if (worldChest == null) {
                statuses += ChestStatus.Missing(desired)
            } else {
                consumed += worldChest.requiredPosition()
                statuses += statusOf(desired, worldChest)
            }
        }
        world.forEach { worldChest ->
            if (worldChest.requiredPosition() in consumed) return@forEach
            if (worldChest.requiredPosition() in unjoinable) {
                val reason =
                    if (worldChest.name.isBlank()) {
                        UnjoinableReason.UNNAMED
                    } else {
                        UnjoinableReason.DUPLICATE
                    }
                statuses += ChestStatus.Unjoinable(worldChest, reason)
            } else {
                statuses += ChestStatus.Orphan(worldChest)
            }
        }
        return ReconcileReport(statuses)
    }

    private fun statusOf(file: UiChest, world: UiChest): ChestStatus {
        val drift =
            ChestDrift(
                rowsDiffer = file.rows != world.rows,
                nameDiffer = file.name != world.name,
                differences = differences(file, world),
            )
        val changed = drift.rowsDiffer || drift.nameDiffer || drift.differences.isNotEmpty()
        return if (changed) {
            ChestStatus.Updated(
                file,
                world,
                drift
            )
        } else {
            ChestStatus.InSync(file, world)
        }
    }

    private fun differences(file: UiChest, world: UiChest): List<SlotDifference> {
        val fileSlots = file.slotMap()
        val worldSlots = world.slotMap()
        val keys = (fileSlots.keys + worldSlots.keys).toSortedSet(SLOT_ORDER)
        return keys.mapNotNull { key ->
            val fileSlot = fileSlots[key]
            val worldSlot = worldSlots[key]
            if (fileSlot == worldSlot) {
                null
            } else {
                SlotDifference(key.first, key.second, fileSlot, worldSlot)
            }
        }
    }

    private fun UiChest.slotMap(): Map<Pair<Int, Int>, NormalizedSlot> =
        content
            .flatMap { row -> row.slots.map { row.row to it } }
            .associate { (row, slot) ->
                (row to slot.slot) to
                    NormalizedSlot(slot.item, slot.name?.takeIf { it.isNotBlank() })
            }

    private fun normalizeName(name: String): String = name.trim().lowercase()

    private val SLOT_ORDER = compareBy<Pair<Int, Int>>({ it.first }, { it.second })
}
