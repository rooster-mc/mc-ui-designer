package dev.cypdashuhn.uidesigner.export

import dev.cypdashuhn.uidesigner.model.BlockPos
import dev.cypdashuhn.uidesigner.model.DesignJson
import dev.cypdashuhn.uidesigner.model.UiChest
import dev.cypdashuhn.uidesigner.model.UiRow
import dev.cypdashuhn.uidesigner.model.UiSlot
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

class JsonExporterTest {
    @Test
    fun `named and unnamed chests serialize to the documented snapshot`() {
        val chests =
            listOf(
                UiChest(
                    name = "Shop",
                    rows = 6,
                    content =
                        listOf(
                            UiRow(row = 1, slots = listOf(slot(1, "minecraft:stone", "Stone")))
                        ),
                    position = BlockPos(0, 0, 0),
                ),
                UiChest(
                    rows = 3,
                    content = listOf(UiRow(row = 1, slots = listOf(slot(2, "minecraft:diamond")))),
                    position = BlockPos(1, 0, 0),
                ),
            )

        assertEquals(EXPECTED_SNAPSHOT, JsonExporter.toJson(chests))
    }

    @Test
    fun `chests, rows and slots are sorted regardless of input order`() {
        val unsorted =
            listOf(
                chest("fourth", BlockPos(1, 0, 0)),
                chest(
                    "third",
                    BlockPos(0, 1, 0),
                    UiRow(row = 2, slots = listOf(slot(2, "minecraft:oak_log"))),
                    UiRow(
                        row = 1,
                        slots =
                            listOf(
                                slot(2, "minecraft:birch_log"),
                                slot(1, "minecraft:acacia_log")
                            )
                    ),
                ),
                chest("second", BlockPos(0, 0, 1)),
                chest("first", BlockPos(0, 0, 0)),
            )

        val decoded =
            DesignJson.decodeFromString<List<UiChest>>(JsonExporter.toJson(unsorted))

        assertEquals(listOf("first", "second", "third", "fourth"), decoded.map { it.name })
        assertEquals(listOf(1, 2), decoded[2].content.map { it.row })
        assertEquals(listOf(1, 2), decoded[2].content[0].slots.map { it.slot })
    }

    @Test
    fun `a chest without a position fails fast instead of falling back to input order`() {
        val chest = UiChest(name = "Shop", rows = 3, content = emptyList())

        assertThrows<IllegalArgumentException> { JsonExporter.toJson(listOf(chest)) }
    }

    @Test
    fun `rows without items are omitted`() {
        val chests =
            listOf(
                chest(
                    "Shop",
                    BlockPos(0, 0, 0),
                    UiRow(row = 2, slots = emptyList()),
                    UiRow(row = 1, slots = listOf(slot(1, "minecraft:stone"))),
                ),
            )

        val decoded = DesignJson.decodeFromString<List<UiChest>>(JsonExporter.toJson(chests))

        assertEquals(listOf(1), decoded.single().content.map { it.row })
    }

    @Test
    fun `a blank chest name is treated as unnamed`() {
        val chests =
            listOf(
                chest(
                    "   ",
                    BlockPos(0, 0, 0),
                    UiRow(row = 1, slots = listOf(slot(1, "minecraft:stone")))
                )
            )

        val decoded = DesignJson.decodeFromString<List<UiChest>>(JsonExporter.toJson(chests))

        assertNull(decoded.single().name)
    }

    @Test
    fun `quotes, backslashes and unicode are escaped in the output`() {
        val special = "Café \"Special\" \\"
        val chests =
            listOf(
                chest(
                    special,
                    BlockPos(0, 0, 0),
                    UiRow(row = 1, slots = listOf(slot(1, "minecraft:stone", special)))
                )
            )

        assertEquals(EXPECTED_ESCAPED_SNAPSHOT, JsonExporter.toJson(chests))
    }

    @Test
    fun `empty input produces an empty JSON array`() {
        assertEquals("[]", JsonExporter.toJson(emptyList()))
    }

    @Test
    fun `export writes the JSON to the target path and leaves no temp file`(
        @TempDir directory: Path
    ) {
        val target = directory.resolve("design.json")
        Files.writeString(target, "stale")
        val chests =
            listOf(
                chest(
                    "Shop",
                    BlockPos(0, 0, 0),
                    UiRow(row = 1, slots = listOf(slot(1, "minecraft:stone")))
                )
            )

        JsonExporter.export(chests, target)

        assertEquals(JsonExporter.toJson(chests), Files.readString(target))
        Files.list(directory).use { stream ->
            assertEquals(
                listOf("design.json"),
                stream.map { it.fileName.toString() }.sorted().toList()
            )
        }
    }

    @Test
    fun `export creates missing parent directories`(
        @TempDir directory: Path
    ) {
        val target = directory.resolve("nested/design.json")
        val chests =
            listOf(
                chest(
                    "Shop",
                    BlockPos(0, 0, 0),
                    UiRow(row = 1, slots = listOf(slot(1, "minecraft:stone")))
                )
            )

        JsonExporter.export(chests, target)

        assertEquals(JsonExporter.toJson(chests), Files.readString(target))
    }

    @Test
    fun `export writes a world-readable file where the filesystem supports posix`(
        @TempDir directory: Path
    ) {
        assumeTrue(directory.fileSystem.supportedFileAttributeViews().contains("posix"))
        val target = directory.resolve("design.json")

        JsonExporter.export(listOf(chest("Shop", BlockPos(0, 0, 0))), target)

        val permissions = Files.getPosixFilePermissions(target)
        assertTrue(permissions.contains(PosixFilePermission.GROUP_READ))
        assertTrue(permissions.contains(PosixFilePermission.OTHERS_READ))
    }

    private fun chest(name: String?, position: BlockPos, vararg content: UiRow) =
        UiChest(name = name, rows = 3, content = content.toList(), position = position)

    private fun slot(slot: Int, item: String, name: String? = null) =
        UiSlot(slot = slot, item = item, name = name)

    private companion object {
        val EXPECTED_SNAPSHOT =
            """
            |[
            |    {
            |        "name": "Shop",
            |        "rows": 6,
            |        "content": [
            |            {
            |                "row": 1,
            |                "slots": [
            |                    {
            |                        "slot": 1,
            |                        "item": "minecraft:stone",
            |                        "name": "Stone"
            |                    }
            |                ]
            |            }
            |        ]
            |    },
            |    {
            |        "rows": 3,
            |        "content": [
            |            {
            |                "row": 1,
            |                "slots": [
            |                    {
            |                        "slot": 2,
            |                        "item": "minecraft:diamond"
            |                    }
            |                ]
            |            }
            |        ]
            |    }
            |]
            """.trimMargin()

        val EXPECTED_ESCAPED_SNAPSHOT =
            """
            |[
            |    {
            |        "name": "Café \"Special\" \\",
            |        "rows": 3,
            |        "content": [
            |            {
            |                "row": 1,
            |                "slots": [
            |                    {
            |                        "slot": 1,
            |                        "item": "minecraft:stone",
            |                        "name": "Café \"Special\" \\"
            |                    }
            |                ]
            |            }
            |        ]
            |    }
            |]
            """.trimMargin()
    }
}
