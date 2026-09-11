package dev.cypdashuhn.uidesigner.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
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
        assertEquals(NamedTextColor.GREEN, Messages.saveSuccess(1, path).color())
        assertEquals(NamedTextColor.GREEN, Messages.reloadSuccess(path).color())
        assertEquals(NamedTextColor.GREEN, Messages.chestEditNamed("Shop").color())
        assertEquals(NamedTextColor.GREEN, Messages.chestEditCleared().color())
    }

    @Test
    fun `errors are red`() {
        assertEquals(NamedTextColor.RED, Messages.noSelection().color())
        assertEquals(NamedTextColor.RED, Messages.noChests().color())
        assertEquals(NamedTextColor.RED, Messages.writeFailed(path, "disk full").color())
        assertEquals(NamedTextColor.RED, Messages.reloadFailed("bad config").color())
        assertEquals(NamedTextColor.RED, Messages.chestEditNotAChest().color())
        assertEquals(NamedTextColor.RED, Messages.noPermission().color())
    }

    @Test
    fun `help lists the subcommands`() {
        val text = plain(Messages.help())
        assertTrue(text.contains("save"))
        assertTrue(text.contains("reload"))
        assertTrue(text.contains("help"))
    }

    @Test
    fun `save success reports the count and path with the double-chest note`() {
        val text = plain(Messages.saveSuccess(2, path))
        assertTrue(text.contains("2 chest designs"))
        assertTrue(text.contains(path.toString()))
        assertTrue(text.contains("double chest counts once"))
    }

    @Test
    fun `save success uses the singular for one design`() {
        assertTrue(plain(Messages.saveSuccess(1, path)).contains("1 chest design"))
    }

    @Test
    fun `no permission is an actionable denial`() {
        assertTrue(plain(Messages.noPermission()).contains("permission"))
    }

    @Test
    fun `reload using defaults names the path and the fallback`() {
        val text = plain(Messages.reloadUsingDefaults(path))
        assertTrue(text.contains("could not be read"))
        assertTrue(text.contains("defaults"))
        assertTrue(text.contains(path.toString()))
    }

    @Test
    fun `chest edit usage explains naming and clearing`() {
        val text = plain(Messages.chestEditUsage())
        assertTrue(text.contains("Usage"))
        assertTrue(text.contains("<name>"))
        assertTrue(text.contains("clear"))
    }

    private fun allMessages(): List<Pair<String, Component>> =
        listOf(
            "help" to Messages.help(),
            "saveSuccess" to Messages.saveSuccess(2, path),
            "noSelection" to Messages.noSelection(),
            "noChests" to Messages.noChests(),
            "writeFailed" to Messages.writeFailed(path, "disk full"),
            "writeFailedWithoutPath" to Messages.writeFailed(null, "disk full"),
            "reloadSuccess" to Messages.reloadSuccess(path),
            "reloadUsingDefaults" to Messages.reloadUsingDefaults(path),
            "reloadFailed" to Messages.reloadFailed("bad config"),
            "chestEditNamed" to Messages.chestEditNamed("Shop"),
            "chestEditCleared" to Messages.chestEditCleared(),
            "chestEditNotAChest" to Messages.chestEditNotAChest(),
            "chestEditUsage" to Messages.chestEditUsage(),
            "noPermission" to Messages.noPermission(),
        )

    private fun plain(message: Component): String =
        PlainTextComponentSerializer.plainText().serialize(message)
}
