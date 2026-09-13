package dev.cypdashuhn.uidesigner.export

import kotlinx.serialization.decodeFromString
import java.nio.file.Files
import java.nio.file.Path

class InvalidDesignException(
    val file: Path,
    val detail: String,
) : Exception("$file: $detail")

object JsonImporter {
    fun read(file: Path, materialMatcher: (String) -> Boolean): List<UiChest> {
        val text = Files.readString(file)
        val chests = DesignJson.decodeFromString<List<UiChest>>(text)
        validate(file, chests, materialMatcher)
        return chests
    }

    private fun validate(file: Path, chests: List<UiChest>, materialMatcher: (String) -> Boolean) {
        if (chests.isEmpty()) {
            fail(file, "the design contains no chests.")
        }
        val seen = mutableMapOf<String, UiChest>()
        chests.forEachIndexed { index, chest ->
            val label = label(index, chest)
            if (chest.name.isBlank()) {
                fail(file, "$label has a blank name; every chest needs a name.")
            }
            val previous = seen.putIfAbsent(chest.name.trim().lowercase(), chest)
            if (previous != null) {
                fail(
                    file,
                    "duplicate chest name \"${chest.name}\" at $label; \"${previous.name}\" " +
                        "is already used (names compare ignoring case and surrounding spaces).",
                )
            }
            if (chest.rows !in ROW_RANGE) {
                fail(
                    file,
                    "$label has rows=${chest.rows}; expected " +
                        "${ROW_RANGE.first}..${ROW_RANGE.last}.",
                )
            }
            chest.content.forEach { row ->
                if (row.row !in 1..chest.rows) {
                    fail(file, "$label has row=${row.row}; expected 1..${chest.rows}.")
                }
                row.slots.forEach { slot ->
                    if (slot.slot !in SLOT_RANGE) {
                        fail(
                            file,
                            "$label row ${row.row} has slot=${slot.slot}; expected " +
                                "${SLOT_RANGE.first}..${SLOT_RANGE.last}.",
                        )
                    }
                    if (!materialMatcher(slot.item)) {
                        fail(
                            file,
                            "$label row ${row.row} slot ${slot.slot} has unknown item " +
                                "\"${slot.item}\".",
                        )
                    }
                }
            }
        }
    }

    private fun label(index: Int, chest: UiChest): String {
        val name = chest.name.trim()
        return if (name.isEmpty()) "entry #${index + 1}" else "entry #${index + 1} (\"$name\")"
    }

    private fun fail(file: Path, detail: String): Nothing =
        throw InvalidDesignException(file, detail)

    private val ROW_RANGE = 1..6
    private val SLOT_RANGE = 1..9
}
