package dev.cypdashuhn.uidesigner.commands

import dev.cypdashuhn.uidesigner.capture.Region
import dev.cypdashuhn.uidesigner.capture.SelectionSource
import dev.cypdashuhn.uidesigner.config.ReloadResult
import dev.cypdashuhn.uidesigner.config.UiDesignerConfig
import dev.cypdashuhn.uidesigner.export.JsonExporter
import dev.cypdashuhn.uidesigner.model.BlockPos
import dev.cypdashuhn.uidesigner.model.DesignJson
import dev.cypdashuhn.uidesigner.model.UiChest
import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.cypdashuhn.uidesigner.util.Messages
import dev.jorel.commandapi.CommandAPITestUtilities
import dev.jorel.commandapi.MockCommandAPIPlugin
import kotlinx.serialization.decodeFromString
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Chest
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.block.data.ChestDataMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import org.mockbukkit.mockbukkit.world.WorldMock
import java.nio.file.Files
import java.nio.file.Path
import org.bukkit.block.data.type.Chest as ChestData

class UiDesignerCommandTest {
    private lateinit var server: ServerMock
    private lateinit var world: WorldMock
    private lateinit var player: PlayerMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        world = server.addSimpleWorld("world")
        world.getChunkAt(0, 0)
        player = server.addPlayer()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `save reports no selection and writes nothing`() {
        val calls = mutableListOf<List<UiChest>>()

        val outcome =
            unregisteredCommand(
                region = null,
                exporter = { chests, _ -> calls += chests },
            ).save(player)

        assertEquals(UiDesignerCommand.SaveOutcome.NoSelection, outcome)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `save reports an empty selection and writes nothing`() {
        blockAt(Material.STONE, 0, 0, 0)
        val calls = mutableListOf<List<UiChest>>()

        val outcome =
            unregisteredCommand(
                region = region(0, 0, 0, 0, 0, 0),
                exporter = { chests, _ -> calls += chests },
            ).save(player)

        assertEquals(UiDesignerCommand.SaveOutcome.NoChests, outcome)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `save exports named and unnamed chests to the configured path`(
        @TempDir directory: Path
    ) {
        val named = blockAt(Material.CHEST, 0, 0, 0)
        (named.state as Chest).blockInventory.setItem(0, ItemStack(Material.STONE))
        ChestNamer.setName(named, "Shop")
        val unnamed = blockAt(Material.CHEST, 2, 0, 0)
        (unnamed.state as Chest).blockInventory.setItem(0, ItemStack(Material.DIRT))
        var exported: List<UiChest>? = null
        var target: Path? = null
        val output = directory.resolve("design.json")

        val outcome =
            unregisteredCommand(
                region = region(0, 0, 0, 2, 0, 0),
                outputFile = output,
                exporter = { chests, path ->
                    exported = chests
                    target = path
                },
            ).save(player)

        assertEquals(UiDesignerCommand.SaveOutcome.Exported(2, output), outcome)
        assertEquals(output, target)
        val exportedChests = checkNotNull(exported)
        assertEquals(listOf("Shop", null), exportedChests.map { it.name })
        assertEquals(
            listOf(BlockPos(0, 0, 0), BlockPos(2, 0, 0)),
            exportedChests.map { it.position },
        )
    }

    @Test
    fun `save counts a double chest as a single design`(
        @TempDir directory: Path
    ) {
        val left = blockAt(Material.CHEST, 0, 0, 0)
        left.blockData =
            ChestDataMock(Material.CHEST).apply {
                type = ChestData.Type.LEFT
                facing = BlockFace.NORTH
            }
        val right = blockAt(Material.CHEST, 1, 0, 0)
        right.blockData =
            ChestDataMock(Material.CHEST).apply {
                type = ChestData.Type.RIGHT
                facing = BlockFace.NORTH
            }
        (left.state as Chest).blockInventory.setItem(0, ItemStack(Material.STONE))
        (right.state as Chest).blockInventory.setItem(0, ItemStack(Material.DIRT))
        var exported: List<UiChest>? = null
        val output = directory.resolve("design.json")

        val outcome =
            unregisteredCommand(
                region = region(0, 0, 0, 1, 0, 0),
                outputFile = output,
                exporter = { chests, _ -> exported = chests },
            ).save(player)

        assertEquals(UiDesignerCommand.SaveOutcome.Exported(1, output), outcome)
        val exportedChests = checkNotNull(exported)
        assertEquals(1, exportedChests.size)
        assertEquals(6, exportedChests.single().rows)
        assertNull(exportedChests.single().name)
    }

    @Test
    fun `save writes the exported design to the configured file`(
        @TempDir directory: Path
    ) {
        val chest = blockAt(Material.CHEST, 0, 0, 0)
        // MockBukkit's Chest.update(true) drops an inventory filled before naming, so fill after.
        ChestNamer.setName(chest, "Shop")
        (chest.state as Chest).blockInventory.setItem(0, ItemStack(Material.STONE))
        val output = directory.resolve("design.json")

        val outcome =
            unregisteredCommand(
                region = region(0, 0, 0, 0, 0, 0),
                outputFile = output,
                exporter = JsonExporter::export,
            ).save(player)

        assertEquals(UiDesignerCommand.SaveOutcome.Exported(1, output), outcome)
        val decoded = DesignJson.decodeFromString<List<UiChest>>(Files.readString(output))
        assertEquals("Shop", decoded.single().name)
        assertEquals(3, decoded.single().rows)
        assertEquals(
            "minecraft:stone",
            decoded
                .single()
                .content
                .single()
                .slots
                .single()
                .item
        )
    }

    @Test
    fun `save reports a write failure without throwing`() {
        blockAt(Material.CHEST, 0, 0, 0)

        val outcome =
            unregisteredCommand(
                region = region(0, 0, 0, 0, 0, 0),
                exporter = { _, _ -> throw java.io.IOException("disk full") },
            ).save(player)

        assertEquals(
            UiDesignerCommand.SaveOutcome.WriteFailed(DEFAULT_OUTPUT, "disk full"),
            outcome,
        )
    }

    @Test
    fun `save reports an unusable config path without throwing`() {
        blockAt(Material.CHEST, 0, 0, 0)
        val command =
            UiDesignerCommand(
                plugin = MockBukkit.createMockPlugin(),
                selectionSource = FakeSelectionSource(region(0, 0, 0, 0, 0, 0)),
                configProvider = { throw IllegalStateException("missing default output") },
                reloadAction = { ReloadResult.Reloaded },
            )

        val outcome = command.save(player)

        assertEquals(
            UiDesignerCommand.SaveOutcome.InvalidOutputFile("missing default output"),
            outcome,
        )
    }

    @Test
    fun `save reports an unusable config path without a message`() {
        blockAt(Material.CHEST, 0, 0, 0)
        val command =
            UiDesignerCommand(
                plugin = MockBukkit.createMockPlugin(),
                selectionSource = FakeSelectionSource(region(0, 0, 0, 0, 0, 0)),
                configProvider = { throw IllegalStateException() },
                reloadAction = { ReloadResult.Reloaded },
            )

        val outcome = command.save(player)

        assertEquals(UiDesignerCommand.SaveOutcome.InvalidOutputFile(null), outcome)
    }

    @Test
    fun `reload runs the injected reload then reads the config`(
        @TempDir directory: Path
    ) {
        var current = directory.resolve("before.json")
        val after = directory.resolve("after.json")
        var reloaded = false
        val command =
            UiDesignerCommand(
                plugin = MockBukkit.createMockPlugin(),
                selectionSource = FakeSelectionSource(null),
                configProvider = { config(current) },
                reloadAction = {
                    reloaded = true
                    current = after
                    ReloadResult.Reloaded
                },
            )

        val outcome = command.reload()

        assertTrue(reloaded)
        assertEquals(UiDesignerCommand.ReloadOutcome.Reloaded(after), outcome)
    }

    @Test
    fun `reload reports defaults when the config could not be read`() {
        val command =
            UiDesignerCommand(
                plugin = MockBukkit.createMockPlugin(),
                selectionSource = FakeSelectionSource(null),
                configProvider = { config(DEFAULT_OUTPUT) },
                reloadAction = { ReloadResult.UsingDefaults },
            )

        assertEquals(
            UiDesignerCommand.ReloadOutcome.UsingDefaults(DEFAULT_OUTPUT),
            command.reload(),
        )
    }

    @Test
    fun `reload reports an invalid output file`() {
        val command =
            UiDesignerCommand(
                plugin = MockBukkit.createMockPlugin(),
                selectionSource = FakeSelectionSource(null),
                configProvider = { config(DEFAULT_OUTPUT) },
                reloadAction = { ReloadResult.InvalidOutput },
            )

        assertEquals(
            UiDesignerCommand.ReloadOutcome.InvalidOutput(DEFAULT_OUTPUT),
            command.reload(),
        )
    }

    @Test
    fun `reload reports a failure without throwing`() {
        val command =
            UiDesignerCommand(
                plugin = MockBukkit.createMockPlugin(),
                selectionSource = FakeSelectionSource(null),
                configProvider = { config(DEFAULT_OUTPUT) },
                reloadAction = { throw IllegalStateException("bad config") },
            )

        assertEquals(UiDesignerCommand.ReloadOutcome.Failed("bad config"), command.reload())
    }

    @Test
    fun `dispatch of save exports for an op`() {
        blockAt(Material.CHEST, 0, 0, 0)
        var exported = false
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(
            plugin,
            region(0, 0, 0, 0, 0, 0),
            exporter = { _, _ -> exported = true },
        )
        player.isOp = true

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner save")

        assertTrue(exported)
    }

    @Test
    fun `dispatch of save is denied without permission`() {
        blockAt(Material.CHEST, 0, 0, 0)
        var exported = false
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(
            plugin,
            region(0, 0, 0, 0, 0, 0),
            exporter = { _, _ -> exported = true },
        )

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner save")

        assertFalse(exported)
        assertEquals(
            Messages.noPermission(UiDesignerCommand.SAVE_PERMISSION),
            player.nextComponentMessage(),
        )
    }

    @Test
    fun `dispatch of save succeeds with the permission node on a non-op`() {
        blockAt(Material.CHEST, 0, 0, 0)
        var exported = false
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(
            plugin,
            region(0, 0, 0, 0, 0, 0),
            exporter = { _, _ -> exported = true },
        )
        player.addAttachment(plugin, UiDesignerCommand.SAVE_PERMISSION, true)

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner save")

        assertTrue(exported)
    }

    @Test
    fun `dispatch of save reports an unusable output path`() {
        blockAt(Material.CHEST, 0, 0, 0)
        val plugin = MockCommandAPIPlugin.load()
        UiDesignerCommand(
            plugin = plugin,
            selectionSource = FakeSelectionSource(region(0, 0, 0, 0, 0, 0)),
            configProvider = { throw IllegalStateException("missing default output") },
            reloadAction = { ReloadResult.Reloaded },
        ).register()
        player.isOp = true

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner save")

        val message = plainMessage()
        assertTrue(message.contains("configured output path"))
        assertTrue(message.contains("missing default output"))
    }

    @Test
    fun `dispatch of reload runs the reload for an op`() {
        var reloaded = false
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(
            plugin,
            reloadAction = {
                reloaded = true
                ReloadResult.Reloaded
            },
        )
        player.isOp = true

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner reload")

        assertTrue(reloaded)
    }

    @Test
    fun `dispatch of reload reports a failure without throwing`() {
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(plugin, reloadAction = { throw IllegalStateException("bad config") })
        player.isOp = true

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner reload")

        val message = plainMessage()
        assertTrue(message.contains("Could not reload config.yml"))
        assertTrue(message.contains("bad config"))
    }

    @Test
    fun `dispatch of reload reports defaults when the config could not be read`() {
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(plugin, reloadAction = { ReloadResult.UsingDefaults })
        player.isOp = true

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner reload")

        val message = plainMessage()
        assertTrue(message.contains("could not be read"))
        assertTrue(message.contains("defaults"))
        assertTrue(message.contains(DEFAULT_OUTPUT.toString()))
    }

    @Test
    fun `dispatch of reload warns when output-file is invalid`() {
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(plugin, reloadAction = { ReloadResult.InvalidOutput })
        player.isOp = true

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner reload")

        val message = plainMessage()
        assertTrue(message.contains("output-file"))
        assertTrue(message.contains("not a valid path"))
        assertTrue(message.contains(DEFAULT_OUTPUT.toString()))
    }

    @Test
    fun `console can reload`() {
        var reloaded = false
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(
            plugin,
            reloadAction = {
                reloaded = true
                ReloadResult.Reloaded
            },
        )

        CommandAPITestUtilities.assertCommandSucceeds(server.consoleSender, "uidesigner reload")

        assertTrue(reloaded)
    }

    @Test
    fun `dispatch of reload is denied without permission`() {
        var reloaded = false
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(
            plugin,
            reloadAction = {
                reloaded = true
                ReloadResult.Reloaded
            },
        )

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner reload")

        assertFalse(reloaded)
        assertEquals(
            Messages.noPermission(UiDesignerCommand.RELOAD_PERMISSION),
            player.nextComponentMessage(),
        )
    }

    @Test
    fun `help needs no permission and lists the subcommands`() {
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(plugin)

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner help")

        val message = plainMessage()
        assertTrue(message.contains("save"))
        assertTrue(message.contains("reload"))
        assertTrue(message.contains("help"))
        assertTrue(message.contains("chest-edit"))
        assertTrue(message.contains("(op)"))
    }

    @Test
    fun `console can print help`() {
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(plugin)

        CommandAPITestUtilities.assertCommandSucceeds(server.consoleSender, "uidesigner help")
    }

    @Test
    fun `bare command prints help`() {
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(plugin)

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner")

        assertTrue(plainMessage().contains("save"))
    }

    @Test
    fun `alias uid resolves the command`() {
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(plugin)

        CommandAPITestUtilities.assertCommandSucceeds(player, "uid help")

        assertTrue(plainMessage().contains("save"))
    }

    @Test
    fun `tab completion lists the uidesigner subcommands`() {
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(plugin)

        CommandAPITestUtilities.assertCommandSuggests(
            player,
            "uidesigner ",
            "help",
            "reload",
            "save",
        )
    }

    private fun plainMessage(): String =
        PlainTextComponentSerializer
            .plainText()
            .serialize(checkNotNull(player.nextComponentMessage()))

    private fun registeredCommand(
        plugin: JavaPlugin,
        region: Region? = null,
        outputFile: Path = DEFAULT_OUTPUT,
        reloadAction: () -> ReloadResult = { ReloadResult.Reloaded },
        exporter: (List<UiChest>, Path) -> Unit = { _, _ -> },
    ) {
        UiDesignerCommand(
            plugin = plugin,
            selectionSource = FakeSelectionSource(region),
            configProvider = { config(outputFile) },
            reloadAction = reloadAction,
            exporter = exporter,
        ).register()
    }

    private fun unregisteredCommand(
        region: Region? = null,
        outputFile: Path = DEFAULT_OUTPUT,
        exporter: (List<UiChest>, Path) -> Unit = { _, _ -> },
    ): UiDesignerCommand =
        UiDesignerCommand(
            plugin = MockBukkit.createMockPlugin(),
            selectionSource = FakeSelectionSource(region),
            configProvider = { config(outputFile) },
            reloadAction = { ReloadResult.Reloaded },
            exporter = exporter,
        )

    private fun config(outputFile: Path): UiDesignerConfig =
        UiDesignerConfig(
            YamlConfiguration().apply {
                set(UiDesignerConfig.OUTPUT_FILE_KEY, outputFile.toString())
            },
            // Tests pass absolute paths, so this data folder is never resolved.
            dataFolder = Path.of(""),
        )

    private fun blockAt(material: Material, x: Int, y: Int, z: Int): Block =
        world.getBlockAt(x, y, z).apply { type = material }

    private fun region(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Region =
        Region.of(BlockPos(minX, minY, minZ), BlockPos(maxX, maxY, maxZ), world)

    private class FakeSelectionSource(
        private val region: Region?
    ) : SelectionSource {
        override fun selectionOf(player: Player): Region? = region
    }

    private companion object {
        val DEFAULT_OUTPUT: Path = Path.of("/tmp/uidesigner-test/design.json")
    }
}
