package dev.cypdashuhn.uidesigner.export

import dev.rooster.region.BlockPos

data class DuplicateNameGroup(
    val name: String,
    val positions: List<BlockPos>,
)

data class ExportValidation(
    val unnamed: List<BlockPos>,
    val duplicates: List<DuplicateNameGroup>,
) {
    val isValid: Boolean = unnamed.isEmpty() && duplicates.isEmpty()
}

fun validateForExport(chests: List<UiChest>): ExportValidation {
    val unnamed = chests.filter { it.name.isBlank() }.map { it.requiredPosition() }
    val duplicates =
        chests
            .filter { it.name.isNotBlank() }
            .groupBy { it.name.trim().lowercase() }
            .values
            .filter { it.size > 1 }
            .map { group ->
                DuplicateNameGroup(
                    name = group.first().name,
                    positions = group.map { it.requiredPosition() },
                )
            }
    return ExportValidation(unnamed, duplicates)
}
