package dev.cypdashuhn.uidesigner.util

import dev.cypdashuhn.uidesigner.commands.helpMessage
import dev.cypdashuhn.uidesigner.commands.invalidNamesMessage
import dev.cypdashuhn.uidesigner.commands.invalidOutputFileMessage
import dev.cypdashuhn.uidesigner.commands.namedMessage
import dev.cypdashuhn.uidesigner.commands.noChestsMessage
import dev.cypdashuhn.uidesigner.commands.noSelectionMessage
import dev.cypdashuhn.uidesigner.commands.noTargetMessage
import dev.cypdashuhn.uidesigner.commands.notAChestMessage
import dev.cypdashuhn.uidesigner.commands.reloadFailedMessage
import dev.cypdashuhn.uidesigner.commands.reloadInvalidOutputMessage
import dev.cypdashuhn.uidesigner.commands.reloadSuccessMessage
import dev.cypdashuhn.uidesigner.commands.reloadUsingDefaultsMessage
import dev.cypdashuhn.uidesigner.commands.saveSuccessMessage
import dev.cypdashuhn.uidesigner.commands.scaffoldIoFailureMessage
import dev.cypdashuhn.uidesigner.commands.scaffoldNoTargetMessage
import dev.cypdashuhn.uidesigner.commands.scaffoldObstructedMessage
import dev.cypdashuhn.uidesigner.commands.scaffoldParseFailureMessage
import dev.cypdashuhn.uidesigner.commands.scaffoldSuccessMessage
import dev.cypdashuhn.uidesigner.commands.usageMessage
import dev.cypdashuhn.uidesigner.commands.writeFailedMessage
import dev.cypdashuhn.uidesigner.export.DuplicateNameGroup
import dev.cypdashuhn.uidesigner.export.NamedPosition
import dev.rooster.region.BlockPos
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class MessagesTest {
    private val path = Path.of("/tmp/uidesigner/design.json")

    @Test
    fun `every message carries the plugin prefix and ends with a period`() {
        allMessages().forEach { (name, message) ->
            val text = plain(message)
            assertTrue(text.isNotBlank(), "$name should not be blank")
            assertTrue(
                text.startsWith(Messages.PREFIX),
                "$name should start with the prefix: $text"
            )
            assertTrue(text.endsWith("."), "$name should end with a period: $text")
        }
    }

    @Test
    fun `every message uses a colour from the shared palette`() {
        val palette = setOf(NamedTextColor.GREEN, NamedTextColor.RED, NamedTextColor.YELLOW)
        allMessages().forEach { (name, message) ->
            assertTrue(message.color() in palette, "$name should use a palette colour")
        }
    }

    @Test
    fun `every message paints the prefix aqua`() {
        allMessages().forEach { (name, message) ->
            assertEquals(NamedTextColor.AQUA, message.children().first().color(), name)
        }
    }

    @Test
    fun `success messages are green`() {
        assertEquals(NamedTextColor.GREEN, saveSuccessMessage(1, path).color())
        assertEquals(NamedTextColor.GREEN, scaffoldSuccessMessage(1, path).color())
        assertEquals(NamedTextColor.GREEN, reloadSuccessMessage(path).color())
        assertEquals(NamedTextColor.GREEN, namedMessage("Shop").color())
    }

    @Test
    fun `errors are red`() {
        assertEquals(NamedTextColor.RED, noSelectionMessage().color())
        assertEquals(NamedTextColor.RED, noChestsMessage().color())
        assertEquals(NamedTextColor.RED, writeFailedMessage(path, "disk full").color())
        assertEquals(NamedTextColor.RED, invalidOutputFileMessage("bad path").color())
        assertEquals(NamedTextColor.RED, reloadFailedMessage("bad config").color())
        assertEquals(NamedTextColor.RED, noTargetMessage().color())
        assertEquals(NamedTextColor.RED, notAChestMessage().color())
        assertEquals(NamedTextColor.RED, scaffoldNoTargetMessage().color())
        assertEquals(NamedTextColor.RED, scaffoldParseFailureMessage(path, "bad rows").color())
        assertEquals(
            NamedTextColor.RED,
            scaffoldObstructedMessage(1, BlockPos(0, 0, 0), firstIsPlayer = false).color(),
        )
        assertEquals(NamedTextColor.RED, scaffoldIoFailureMessage(path, null).color())
        assertEquals(
            NamedTextColor.RED,
            invalidNamesMessage(listOf(BlockPos(0, 0, 0)), emptyList()).color(),
        )
    }

    @Test
    fun `warnings are yellow`() {
        assertEquals(NamedTextColor.YELLOW, reloadUsingDefaultsMessage(path).color())
        assertEquals(NamedTextColor.YELLOW, reloadInvalidOutputMessage(path).color())
    }

    @Test
    fun `help lists the subcommands`() {
        val text = plain(helpMessage())
        assertTrue(text.contains("save"))
        assertTrue(text.contains("reload"))
        assertTrue(text.contains("help"))
        assertTrue(text.contains("chest-edit"))
    }

    @Test
    fun `save success reports the count and path with the double-chest note`() {
        val text = plain(saveSuccessMessage(2, path))
        assertTrue(text.contains("2 chest designs"))
        assertTrue(text.contains(path.toString()))
        assertTrue(text.contains("double chest counts once"))
    }

    @Test
    fun `save success uses the singular for one design`() {
        assertTrue(plain(saveSuccessMessage(1, path)).contains("1 chest design"))
    }

    @Test
    fun `scaffold success reports the count and path with the double-chest note`() {
        val text = plain(scaffoldSuccessMessage(2, path))
        assertTrue(text.contains("2 chest designs"))
        assertTrue(text.contains(path.toString()))
        assertTrue(text.contains("double chest counts once"))
    }

    @Test
    fun `scaffold success uses the singular for one design`() {
        assertTrue(plain(scaffoldSuccessMessage(1, path)).contains("1 chest design"))
    }

    @Test
    fun `scaffold obstruction names the count the position and the block recovery`() {
        val text = plain(scaffoldObstructedMessage(3, BlockPos(4, 5, 6), firstIsPlayer = false))

        assertTrue(text.contains("3 target blocks are obstructed"))
        assertTrue(text.contains("(4, 5, 6)"))
        assertTrue(text.contains("not replaceable"))
        assertTrue(text.contains("nothing placed"))
    }

    @Test
    fun `scaffold obstruction uses the singular and the player recovery`() {
        val text = plain(scaffoldObstructedMessage(1, BlockPos(4, 5, 6), firstIsPlayer = true))

        assertTrue(text.contains("1 target block is obstructed"))
        assertTrue(text.contains("inside your body"))
        assertTrue(text.contains("step aside"))
    }

    @Test
    fun `scaffold parse failure names the path and the entry`() {
        val text = plain(scaffoldParseFailureMessage(path, "entry #1 has rows=7"))

        assertTrue(text.contains(path.toString()))
        assertTrue(text.contains("entry #1 has rows=7"))
    }

    @Test
    fun `scaffold IO failure names the path and falls back to the hint`() {
        val missing = plain(scaffoldIoFailureMessage(path, null))
        assertTrue(missing.contains(path.toString()))
        assertTrue(missing.contains("check the file exists and is readable"))

        val unresolved = plain(scaffoldIoFailureMessage(null, null))
        assertTrue(unresolved.contains("check the output-file setting"))
    }

    @Test
    fun `scaffold no target points at the anchor fallback`() {
        val text = plain(scaffoldNoTargetMessage())
        assertTrue(text.contains("no world or usable target block"))
        assertTrue(text.contains("open space"))
    }

    @Test
    fun `reload using defaults names the path and the fallback`() {
        val text = plain(reloadUsingDefaultsMessage(path))
        assertTrue(text.contains("could not be read"))
        assertTrue(text.contains("defaults"))
        assertTrue(text.contains(path.toString()))
    }

    @Test
    fun `reload invalid output names the key and the path`() {
        val text = plain(reloadInvalidOutputMessage(path))
        assertTrue(text.contains("output-file"))
        assertTrue(text.contains("not a valid path"))
        assertTrue(text.contains("defaults"))
        assertTrue(text.contains(path.toString()))
    }

    @Test
    fun `invalid output file reports the reason`() {
        val text = plain(invalidOutputFileMessage("check the output-file setting"))
        assertTrue(text.contains("configured output path"))
        assertTrue(text.contains("check the output-file setting"))
    }

    @Test
    fun `failure messages fall back to a hint when the reason is blank`() {
        assertTrue(plain(writeFailedMessage(path, null)).contains("output folder"))
        assertTrue(plain(writeFailedMessage(path, "  ")).contains("output folder"))
        assertTrue(
            plain(invalidOutputFileMessage(null)).contains("check the output-file setting")
        )
        assertTrue(plain(reloadFailedMessage(null)).contains("check config.yml"))
    }

    @Test
    fun `chest edit failure wording distinguishes no target from a non-chest`() {
        val noTarget = plain(noTargetMessage())
        val notAChest = plain(notAChestMessage())
        assertTrue(noTarget.contains("out of reach"))
        assertTrue(notAChest.contains("not a chest"))
        assertTrue(noTarget != notAChest)
    }

    @Test
    fun `chest edit usage explains naming only`() {
        val text = plain(usageMessage())
        assertTrue(text.contains("Usage"))
        assertTrue(text.contains("<name>"))
        assertFalse(text.contains("clear"))
    }

    @Test
    fun `invalid names message lists unnamed positions`() {
        val text =
            plain(
                invalidNamesMessage(
                    unnamed = listOf(BlockPos(0, 0, 0), BlockPos(2, 0, 0)),
                    duplicates = emptyList(),
                )
            )

        assertTrue(text.contains("2 chests have no name"))
        assertTrue(text.contains("(0, 0, 0)"))
        assertTrue(text.contains("(2, 0, 0)"))
        assertTrue(text.contains("/chest-edit <name>"))
    }

    @Test
    fun `invalid names message uses the singular for one unnamed chest`() {
        val text =
            plain(
                invalidNamesMessage(unnamed = listOf(BlockPos(0, 0, 0)), duplicates = emptyList())
            )

        assertTrue(text.contains("1 chest has no name"))
        assertTrue(text.contains("Name it with"))
    }

    @Test
    fun `invalid names message explains case and whitespace duplicates`() {
        val text =
            plain(
                invalidNamesMessage(
                    unnamed = emptyList(),
                    duplicates =
                        listOf(
                            DuplicateNameGroup(
                                listOf(
                                    NamedPosition("Shop", BlockPos(0, 0, 0)),
                                    NamedPosition("SHOP", BlockPos(2, 0, 0)),
                                )
                            )
                        ),
                )
            )

        assertTrue(text.contains("compared ignoring case and surrounding spaces"))
        assertTrue(text.contains("\"Shop\" at (0, 0, 0)"))
        assertTrue(text.contains("\"SHOP\" at (2, 0, 0)"))
    }

    @Test
    fun `invalid names message reports unnamed and duplicates together`() {
        val text =
            plain(
                invalidNamesMessage(
                    unnamed = listOf(BlockPos(4, 0, 0)),
                    duplicates =
                        listOf(
                            DuplicateNameGroup(
                                listOf(
                                    NamedPosition("Shop", BlockPos(0, 0, 0)),
                                    NamedPosition("shop", BlockPos(2, 0, 0)),
                                )
                            )
                        ),
                )
            )

        assertTrue(text.contains("no name"))
        assertTrue(text.contains("(4, 0, 0)"))
        assertTrue(text.contains("must be unique"))
        assertTrue(text.contains("\"shop\" at (2, 0, 0)"))
    }

    private fun allMessages(): List<Pair<String, Component>> =
        listOf(
            "help" to helpMessage(),
            "saveSuccess" to saveSuccessMessage(2, path),
            "scaffoldSuccess" to scaffoldSuccessMessage(2, path),
            "scaffoldNoTarget" to scaffoldNoTargetMessage(),
            "scaffoldParseFailure" to scaffoldParseFailureMessage(path, "entry #1 has rows=7"),
            "scaffoldObstructedBlock" to
                scaffoldObstructedMessage(3, BlockPos(4, 5, 6), firstIsPlayer = false),
            "scaffoldObstructedPlayer" to
                scaffoldObstructedMessage(1, BlockPos(4, 5, 6), firstIsPlayer = true),
            "scaffoldIoFailure" to scaffoldIoFailureMessage(path, null),
            "scaffoldIoFailureNoPath" to scaffoldIoFailureMessage(null, null),
            "noSelection" to noSelectionMessage(),
            "noChests" to noChestsMessage(),
            "writeFailed" to writeFailedMessage(path, "disk full"),
            "writeFailedWithoutPath" to writeFailedMessage(null, "disk full"),
            "writeFailedFallback" to writeFailedMessage(path, null),
            "invalidOutputFile" to invalidOutputFileMessage("bad path"),
            "invalidOutputFileFallback" to invalidOutputFileMessage(null),
            "reloadSuccess" to reloadSuccessMessage(path),
            "reloadUsingDefaults" to reloadUsingDefaultsMessage(path),
            "reloadInvalidOutput" to reloadInvalidOutputMessage(path),
            "reloadFailed" to reloadFailedMessage("bad config"),
            "reloadFailedFallback" to reloadFailedMessage(null),
            "chestEditNamed" to namedMessage("Shop"),
            "saveUnnamed" to invalidNamesMessage(listOf(BlockPos(0, 0, 0)), emptyList()),
            "saveDuplicate" to
                invalidNamesMessage(
                    emptyList(),
                    listOf(
                        DuplicateNameGroup(
                            listOf(
                                NamedPosition("Shop", BlockPos(0, 0, 0)),
                                NamedPosition("shop", BlockPos(2, 0, 0)),
                            )
                        )
                    ),
                ),
            "chestEditNoTarget" to noTargetMessage(),
            "chestEditNotAChest" to notAChestMessage(),
            "chestEditUsage" to usageMessage(),
        )

    private fun plain(message: Component): String =
        PlainTextComponentSerializer.plainText().serialize(message)
}
