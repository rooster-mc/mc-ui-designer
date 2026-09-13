package dev.cypdashuhn.uidesigner.commands

import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.jorel.commandapi.CommandAPITestUtilities
import dev.jorel.commandapi.MockCommandAPIPlugin
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.block.Block
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import org.mockbukkit.mockbukkit.world.WorldMock

class ChestEditCommandTest {
    private lateinit var server: ServerMock
    private lateinit var world: WorldMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        world = server.addSimpleWorld("world")
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `apply names a chest`() {
        val chest = blockAt(Material.CHEST)

        val outcome = command().apply(chest, "Shop")

        assertEquals(ChestEditCommand.Outcome.Named("Shop"), outcome)
        assertEquals("Shop", ChestNamer.nameOf(chest))
    }

    @Test
    fun `apply treats clear as an ordinary name`() {
        val chest = blockAt(Material.CHEST)
        ChestNamer.setName(chest, "Shop")

        val outcome = command().apply(chest, "clear")

        assertEquals(ChestEditCommand.Outcome.Named("clear"), outcome)
        assertEquals("clear", ChestNamer.nameOf(chest))
    }

    @Test
    fun `apply rejects a non-chest block`() {
        val outcome = command().apply(blockAt(Material.STONE), "Shop")

        assertEquals(ChestEditCommand.Outcome.NotAChest, outcome)
    }

    @Test
    fun `apply rejects a null target`() {
        val outcome = command().apply(null, "Shop")

        assertEquals(ChestEditCommand.Outcome.NoTarget, outcome)
    }

    @Test
    fun `apply treats a blank name as usage and leaves the name unchanged`() {
        val chest = blockAt(Material.CHEST)
        ChestNamer.setName(chest, "Shop")

        val outcome = command().apply(chest, "   ")

        assertEquals(ChestEditCommand.Outcome.BlankName, outcome)
        assertEquals("Shop", ChestNamer.nameOf(chest))
    }

    @Test
    fun `apply treats a blank name as usage on an unnamed chest`() {
        val chest = blockAt(Material.CHEST)

        assertEquals(ChestEditCommand.Outcome.BlankName, command().apply(chest, "   "))
        assertNull(ChestNamer.nameOf(chest))
    }

    @Test
    fun `apply trims a whitespace-padded name`() {
        val chest = blockAt(Material.CHEST)

        val outcome = command().apply(chest, "  Shop  ")

        assertEquals(ChestEditCommand.Outcome.Named("Shop"), outcome)
        assertEquals("Shop", ChestNamer.nameOf(chest))
    }

    @Test
    fun `command dispatch names the target chest`() {
        val chest = blockAt(Material.CHEST)
        val plugin = MockCommandAPIPlugin.load()
        ChestEditCommand(plugin) { chest }.register()
        val player = opPlayer()

        CommandAPITestUtilities.assertCommandSucceeds(player, "chest-edit Shop")

        assertEquals("Shop", ChestNamer.nameOf(chest))
    }

    @Test
    fun `command dispatch names a chest clear`() {
        val chest = blockAt(Material.CHEST)
        ChestNamer.setName(chest, "Shop")
        val plugin = MockCommandAPIPlugin.load()
        ChestEditCommand(plugin) { chest }.register()
        val player = opPlayer()

        CommandAPITestUtilities.assertCommandSucceeds(player, "chest-edit clear")

        assertEquals("clear", ChestNamer.nameOf(chest))
    }

    @Test
    fun `command dispatch trims a whitespace-padded name`() {
        val chest = blockAt(Material.CHEST)
        val plugin = MockCommandAPIPlugin.load()
        ChestEditCommand(plugin) { chest }.register()
        val player = opPlayer()

        CommandAPITestUtilities.assertCommandSucceeds(player, "chest-edit  Shop ")

        assertEquals("Shop", ChestNamer.nameOf(chest))
    }

    @Test
    fun `bare chest-edit prints usage and does not name the chest`() {
        val chest = blockAt(Material.CHEST)
        val plugin = MockCommandAPIPlugin.load()
        ChestEditCommand(plugin) { chest }.register()
        val player = opPlayer()

        CommandAPITestUtilities.assertCommandSucceeds(player, "chest-edit")

        val message = plainMessage(player)
        assertTrue(message.contains("Usage"))
        assertTrue(message.contains("<name>"))
        assertFalse(message.contains("clear"))
        assertNull(ChestNamer.nameOf(chest))
    }

    @Test
    fun `console bare chest-edit is a silent no-op`() {
        val plugin = MockCommandAPIPlugin.load()
        ChestEditCommand(plugin) { null }.register()

        CommandAPITestUtilities.assertCommandSucceeds(server.consoleSender, "chest-edit")

        assertNull(server.consoleSender.nextComponentMessage())
    }

    @Test
    fun `tab completion does not suggest clear`() {
        val plugin = MockCommandAPIPlugin.load()
        ChestEditCommand(plugin) { null }.register()

        CommandAPITestUtilities.assertCommandSuggests(
            opPlayer(),
            "chest-edit ",
            emptyList<String>(),
        )
    }

    private fun opPlayer(): PlayerMock = server.addPlayer().apply { isOp = true }

    private fun plainMessage(player: PlayerMock): String =
        PlainTextComponentSerializer
            .plainText()
            .serialize(checkNotNull(player.nextComponentMessage()))

    private fun command() = ChestEditCommand(MockBukkit.createMockPlugin())

    private fun blockAt(material: Material, x: Int = 0): Block =
        world.getBlockAt(x, 0, 0).apply { type = material }
}
