package dev.cypdashuhn.uidesigner.commands

import com.mojang.brigadier.exceptions.CommandSyntaxException
import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.jorel.commandapi.CommandAPITestUtilities
import dev.jorel.commandapi.MockCommandAPIPlugin
import org.bukkit.Material
import org.bukkit.block.Block
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
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
    fun `apply clears a named chest`() {
        val chest = blockAt(Material.CHEST)
        ChestNamer.setName(chest, "Shop")

        val outcome = command().apply(chest, "clear")

        assertEquals(ChestEditCommand.Outcome.Cleared, outcome)
        assertNull(ChestNamer.nameOf(chest))
    }

    @Test
    fun `apply clears case-insensitively`() {
        val chest = blockAt(Material.CHEST)
        ChestNamer.setName(chest, "Shop")

        val outcome = command().apply(chest, "CLEAR")

        assertEquals(ChestEditCommand.Outcome.Cleared, outcome)
        assertNull(ChestNamer.nameOf(chest))
    }

    @Test
    fun `apply treats a blank name as clear`() {
        val chest = blockAt(Material.CHEST)
        ChestNamer.setName(chest, "Shop")

        val outcome = command().apply(chest, "   ")

        assertEquals(ChestEditCommand.Outcome.Cleared, outcome)
        assertNull(ChestNamer.nameOf(chest))
    }

    @Test
    fun `apply rejects a non-chest block`() {
        val outcome = command().apply(blockAt(Material.STONE), "Shop")

        assertEquals(ChestEditCommand.Outcome.NotAChest, outcome)
    }

    @Test
    fun `apply rejects a null target`() {
        val outcome = command().apply(null, "Shop")

        assertEquals(ChestEditCommand.Outcome.NotAChest, outcome)
    }

    @Test
    fun `command dispatch names the target chest`() {
        val chest = blockAt(Material.CHEST)
        val plugin = MockCommandAPIPlugin.load()
        ChestEditCommand(plugin) { chest }.register()
        val player = server.addPlayer()
        player.isOp = true

        CommandAPITestUtilities.assertCommandSucceeds(player, "chest-edit Shop")

        assertEquals("Shop", ChestNamer.nameOf(chest))
    }

    @Test
    fun `command dispatch clears via the reserved sentinel`() {
        val chest = blockAt(Material.CHEST)
        ChestNamer.setName(chest, "Shop")
        val plugin = MockCommandAPIPlugin.load()
        ChestEditCommand(plugin) { chest }.register()
        val player = server.addPlayer()
        player.isOp = true

        CommandAPITestUtilities.assertCommandSucceeds(player, "chest-edit clear")

        assertNull(ChestNamer.nameOf(chest))
    }

    @Test
    fun `command dispatch is denied without permission`() {
        val chest = blockAt(Material.CHEST)
        val plugin = MockCommandAPIPlugin.load()
        ChestEditCommand(plugin) { chest }.register()
        val player = server.addPlayer()

        assertThrows(CommandSyntaxException::class.java) {
            CommandAPITestUtilities.dispatchCommand(player, "chest-edit Shop")
        }
        assertNull(ChestNamer.nameOf(chest))
    }

    @Test
    fun `command dispatch succeeds with the permission node on a non-op`() {
        val chest = blockAt(Material.CHEST)
        val plugin = MockCommandAPIPlugin.load()
        ChestEditCommand(plugin) { chest }.register()
        val player = server.addPlayer()
        player.addAttachment(plugin, "uidesigner.chest-edit", true)

        CommandAPITestUtilities.assertCommandSucceeds(player, "chest-edit Shop")

        assertEquals("Shop", ChestNamer.nameOf(chest))
    }

    private fun command() = ChestEditCommand(MockBukkit.createMockPlugin())

    private fun blockAt(material: Material, x: Int = 0): Block =
        world.getBlockAt(x, 0, 0).apply { type = material }
}
