package dev.cypdashuhn.uidesigner.export

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class JsonImporterTest {
    @Test
    fun `a valid file reads back the chests`(
        @TempDir directory: Path
    ) {
        val file =
            write(
                directory,
                listOf(
                    UiChest("Shop", rows = 6, content = listOf(row(1, slot(1, "minecraft:stone")))),
                    UiChest("Mine", rows = 3, content = emptyList()),
                ),
            )

        val chests = JsonImporter.read(file, ::knownItem)

        assertEquals(listOf("Shop", "Mine"), chests.map { it.name })
        assertEquals(listOf(6, 3), chests.map { it.rows })
    }

    @Test
    fun `rows outside one to six fail naming the file and entry`(
        @TempDir directory: Path
    ) {
        val file = write(directory, listOf(UiChest("Shop", rows = 7, content = emptyList())))

        val error = assertThrows<InvalidDesignException> { JsonImporter.read(file, ::knownItem) }

        assertTrue(error.message!!.contains("design.json"))
        assertTrue(error.message!!.contains("entry #1 (\"Shop\")"))
        assertTrue(error.message!!.contains("rows=7"))
    }

    @Test
    fun `a row outside the chest rows fails naming the file and entry`(
        @TempDir directory: Path
    ) {
        val file =
            write(
                directory,
                listOf(
                    UiChest(
                        "Shop",
                        rows = 3,
                        content = listOf(row(4, slot(1, "minecraft:stone"))),
                    )
                ),
            )

        val error = assertThrows<InvalidDesignException> { JsonImporter.read(file, ::knownItem) }

        assertTrue(error.message!!.contains("design.json"))
        assertTrue(error.message!!.contains("entry #1 (\"Shop\")"))
        assertTrue(error.message!!.contains("row=4"))
    }

    @Test
    fun `a slot outside one to nine fails naming the file and entry`(
        @TempDir directory: Path
    ) {
        val file =
            write(
                directory,
                listOf(
                    UiChest("Shop", rows = 3, content = listOf(row(1, slot(10, "minecraft:stone"))))
                ),
            )

        val error = assertThrows<InvalidDesignException> { JsonImporter.read(file, ::knownItem) }

        assertTrue(error.message!!.contains("design.json"))
        assertTrue(error.message!!.contains("entry #1 (\"Shop\")"))
        assertTrue(error.message!!.contains("slot=10"))
    }

    @Test
    fun `an unknown item fails naming the file and entry`(
        @TempDir directory: Path
    ) {
        val file =
            write(
                directory,
                listOf(
                    UiChest(
                        "Shop",
                        rows = 3,
                        content = listOf(row(1, slot(1, "minecraft:not_a_thing"))),
                    )
                ),
            )

        val error = assertThrows<InvalidDesignException> { JsonImporter.read(file, ::knownItem) }

        assertTrue(error.message!!.contains("design.json"))
        assertTrue(error.message!!.contains("entry #1 (\"Shop\")"))
        assertTrue(error.message!!.contains("minecraft:not_a_thing"))
    }

    @Test
    fun `a blank name fails naming the file and entry`(
        @TempDir directory: Path
    ) {
        val file = write(directory, listOf(UiChest("   ", rows = 3, content = emptyList())))

        val error = assertThrows<InvalidDesignException> { JsonImporter.read(file, ::knownItem) }

        assertTrue(error.message!!.contains("design.json"))
        assertTrue(error.message!!.contains("entry #1"))
        assertTrue(error.message!!.contains("blank name"))
    }

    @Test
    fun `duplicate names fail ignoring case and surrounding spaces`(
        @TempDir directory: Path
    ) {
        val file =
            write(
                directory,
                listOf(
                    UiChest("Shop", rows = 3, content = emptyList()),
                    UiChest("  shop  ", rows = 3, content = emptyList()),
                ),
            )

        val error = assertThrows<InvalidDesignException> { JsonImporter.read(file, ::knownItem) }

        assertTrue(error.message!!.contains("design.json"))
        assertTrue(error.message!!.contains("entry #2 (\"shop\")"))
        assertTrue(error.message!!.contains("\"  shop  \""))
        assertTrue(error.message!!.contains("\"Shop\""))
    }

    @Test
    fun `an empty design fails naming the file`(
        @TempDir directory: Path
    ) {
        val file = write(directory, emptyList())

        val error = assertThrows<InvalidDesignException> { JsonImporter.read(file, ::knownItem) }

        assertTrue(error.message!!.contains("design.json"))
        assertTrue(error.message!!.contains("no chests"))
        assertEquals(file, error.file)
        assertTrue(error.detail.contains("no chests"))
    }

    @Test
    fun `a missing file raises an IO exception`(
        @TempDir directory: Path
    ) {
        val file = directory.resolve("missing.json")

        assertThrows<NoSuchFileException> { JsonImporter.read(file, ::knownItem) }
    }

    @Test
    fun `malformed JSON fails instead of returning a partial list`(
        @TempDir directory: Path
    ) {
        val file = directory.resolve("design.json")
        Files.writeString(file, """[{"name":"Shop","rows":}]""")

        assertThrows<SerializationException> { JsonImporter.read(file, ::knownItem) }
    }

    private fun write(directory: Path, chests: List<UiChest>): Path {
        val file = directory.resolve("design.json")
        Files.writeString(file, DesignJson.encodeToString(chests))
        return file
    }

    private fun row(row: Int, vararg slots: UiSlot): UiRow = UiRow(row, slots.toList())

    private fun slot(slot: Int, item: String): UiSlot = UiSlot(slot, item)

    private fun knownItem(id: String): Boolean = id in KNOWN_ITEMS

    private companion object {
        val KNOWN_ITEMS = setOf("minecraft:stone", "minecraft:diamond")
    }
}
