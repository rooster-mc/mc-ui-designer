package dev.cypdashuhn.uidesigner.commands

import dev.cypdashuhn.uidesigner.capture.ClippedHalf
import dev.cypdashuhn.uidesigner.config.ReloadResult
import dev.cypdashuhn.uidesigner.config.UiDesignerConfig
import dev.cypdashuhn.uidesigner.export.DesignJson
import dev.cypdashuhn.uidesigner.export.DuplicateNameGroup
import dev.cypdashuhn.uidesigner.export.JsonExporter
import dev.cypdashuhn.uidesigner.export.UiChest
import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.jorel.commandapi.CommandAPITestUtilities
import dev.jorel.commandapi.MockCommandAPIPlugin
import dev.rooster.region.BlockPos
import dev.rooster.region.Region
import kotlinx.serialization.decodeFromString
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Chest
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `save exports all-named chests to the configured path`(
        @TempDir directory: Path
    ) {
        val shop = blockAt(Material.CHEST, 0, 0, 0)
        (shop.state as Chest).blockInventory.setItem(0, ItemStack(Material.STONE))
        ChestNamer.setName(shop, "Shop")
        val mine = blockAt(Material.CHEST, 2, 0, 0)
        (mine.state as Chest).blockInventory.setItem(0, ItemStack(Material.DIRT))
        ChestNamer.setName(mine, "Mine")
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
        assertEquals(listOf("Shop", "Mine"), exportedChests.map { it.name })
        assertEquals(
            listOf(BlockPos(0, 0, 0), BlockPos(2, 0, 0)),
            exportedChests.map { it.position },
        )
    }

    @Test
    fun `save reports an unnamed chest and writes no file`(
        @TempDir directory: Path
    ) {
        val shop = blockAt(Material.CHEST, 0, 0, 0)
        ChestNamer.setName(shop, "Shop")
        blockAt(Material.CHEST, 2, 0, 0)
        var exporterCalls = 0
        val output = directory.resolve("design.json")

        val outcome =
            unregisteredCommand(
                region = region(0, 0, 0, 2, 0, 0),
                outputFile = output,
                exporter = { _, _ -> exporterCalls++ },
            ).save(player)

        assertEquals(
            UiDesignerCommand.SaveOutcome.UnnamedChests(listOf(BlockPos(2, 0, 0))),
            outcome,
        )
        assertEquals(0, exporterCalls)
        assertFalse(Files.exists(output))
    }

    @Test
    fun `save reports duplicate names and writes no file`(
        @TempDir directory: Path
    ) {
        val shop = blockAt(Material.CHEST, 0, 0, 0)
        ChestNamer.setName(shop, "Shop")
        val other = blockAt(Material.CHEST, 2, 0, 0)
        ChestNamer.setName(other, "  shop  ")
        var exporterCalls = 0
        val output = directory.resolve("design.json")

        val outcome =
            unregisteredCommand(
                region = region(0, 0, 0, 2, 0, 0),
                outputFile = output,
                exporter = { _, _ -> exporterCalls++ },
            ).save(player)

        assertEquals(
            UiDesignerCommand.SaveOutcome.DuplicateNames(
                listOf(
                    DuplicateNameGroup(
                        name = "Shop",
                        positions = listOf(BlockPos(0, 0, 0), BlockPos(2, 0, 0)),
                    )
                )
            ),
            outcome,
        )
        assertEquals(0, exporterCalls)
        assertFalse(Files.exists(output))
    }

    @Test
    fun `save counts a double chest as a single design`(
        @TempDir directory: Path
    ) {
        val left = blockAt(Material.CHEST, 0, 0, 0)
        ChestNamer.setName(left, "Shop")
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
        assertEquals("Shop", exportedChests.single().name)
    }

    @Test
    fun `save reports a clipped double chest and writes no file`(
        @TempDir directory: Path
    ) {
        clippedHalfChest(0, 0)
        var exporterCalls = 0
        val output = directory.resolve("design.json")

        val outcome =
            unregisteredCommand(
                region = region(0, 0, 0, 0, 0, 0),
                outputFile = output,
                exporter = { _, _ -> exporterCalls++ },
            ).save(player)

        assertEquals(
            UiDesignerCommand.SaveOutcome.ClippedChests(
                listOf(
                    ClippedHalf(
                        position = BlockPos(0, 0, 0),
                        partner = BlockPos(1, 0, 0),
                        partnerInsideSelection = false,
                    ),
                ),
            ),
            outcome,
        )
        assertEquals(0, exporterCalls)
        assertFalse(Files.exists(output))
    }

    @Test
    fun `save aborts on clipped halves even when a valid chest is present`(
        @TempDir directory: Path
    ) {
        clippedHalfChest(0, 0)
        val valid = blockAt(Material.CHEST, 2, 0, 0)
        (valid.state as Chest).blockInventory.setItem(0, ItemStack(Material.STONE))
        clippedHalfChest(4, 0)
        var exporterCalls = 0
        val output = directory.resolve("design.json")

        val outcome =
            unregisteredCommand(
                region = region(0, 0, 0, 5, 0, 0),
                outputFile = output,
                exporter = { _, _ -> exporterCalls++ },
            ).save(player)

        assertEquals(
            UiDesignerCommand.SaveOutcome.ClippedChests(
                listOf(
                    ClippedHalf(BlockPos(0, 0, 0), BlockPos(1, 0, 0), true),
                    ClippedHalf(BlockPos(4, 0, 0), BlockPos(5, 0, 0), true),
                ),
            ),
            outcome,
        )
        assertEquals(0, exporterCalls)
        assertFalse(Files.exists(output))
    }

    @Test
    fun `save aborts on a clipped copper double chest`() {
        clippedHalfChest(0, 0, Material.COPPER_CHEST)
        var exporterCalls = 0

        val outcome =
            unregisteredCommand(
                region = region(0, 0, 0, 0, 0, 0),
                exporter = { _, _ -> exporterCalls++ },
            ).save(player)

        assertEquals(
            UiDesignerCommand.SaveOutcome.ClippedChests(
                listOf(ClippedHalf(BlockPos(0, 0, 0), BlockPos(1, 0, 0), false)),
            ),
            outcome,
        )
        assertEquals(0, exporterCalls)
    }

    @Test
    fun `dispatch of save reports the clipped half and its outside partner`() {
        clippedHalfChest(0, 0)
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(plugin, region(0, 0, 0, 0, 0, 0))
        player.isOp = true

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner save")

        val message = plainMessage()
        assertTrue(message.contains("(0, 0, 0)"))
        assertTrue(message.contains("(1, 0, 0)"))
        assertTrue(message.contains("outside your selection"))
        assertTrue(message.contains("Expand the selection to include both halves"))
    }

    @Test
    fun `dispatch of save reports a partner half that was not captured`() {
        clippedHalfChest(15, 0)
        clippedHalfChest(16, 0, type = ChestData.Type.RIGHT)
        assertFalse(world.isChunkLoaded(1, 0))
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(plugin, region(15, 0, 0, 16, 0, 0))
        player.isOp = true

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner save")

        val message = plainMessage()
        assertTrue(message.contains("(15, 0, 0)"))
        assertTrue(message.contains("(16, 0, 0)"))
        assertTrue(message.contains("chunk is not loaded"))
        assertTrue(message.contains("shrink the selection"))
    }

    @Test
    fun `clipped message names the first half and mentions the rest`() {
        val message =
            PlainTextComponentSerializer
                .plainText()
                .serialize(
                    clippedChestsMessage(
                        listOf(
                            ClippedHalf(BlockPos(0, 0, 0), BlockPos(1, 0, 0), false),
                            ClippedHalf(BlockPos(5, 0, 0), BlockPos(6, 0, 0), false),
                        ),
                    ),
                )

        assertTrue(message.contains("(0, 0, 0)"))
        assertTrue(message.contains("(1, 0, 0)"))
        assertTrue(message.contains("1 more chest is also clipped"))
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
        ChestNamer.setName(blockAt(Material.CHEST, 0, 0, 0), "Shop")

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
        ChestNamer.setName(blockAt(Material.CHEST, 0, 0, 0), "Shop")
        val command =
            UiDesignerCommand(
                plugin = MockBukkit.createMockPlugin(),
                selectionProvider = { region(0, 0, 0, 0, 0, 0) },
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
        ChestNamer.setName(blockAt(Material.CHEST, 0, 0, 0), "Shop")
        val command =
            UiDesignerCommand(
                plugin = MockBukkit.createMockPlugin(),
                selectionProvider = { region(0, 0, 0, 0, 0, 0) },
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
                selectionProvider = { null },
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
                selectionProvider = { null },
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
                selectionProvider = { null },
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
                selectionProvider = { null },
                configProvider = { config(DEFAULT_OUTPUT) },
                reloadAction = { throw IllegalStateException("bad config") },
            )

        assertEquals(UiDesignerCommand.ReloadOutcome.Failed("bad config"), command.reload())
    }

    @Test
    fun `dispatch of save exports for an op`() {
        ChestNamer.setName(blockAt(Material.CHEST, 0, 0, 0), "Shop")
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
    fun `dispatch of save reports an unnamed chest`() {
        ChestNamer.setName(blockAt(Material.CHEST, 0, 0, 0), "Shop")
        blockAt(Material.CHEST, 2, 0, 0)
        var exported = false
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(
            plugin,
            region(0, 0, 0, 2, 0, 0),
            exporter = { _, _ -> exported = true },
        )
        player.isOp = true

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner save")

        val message = plainMessage()
        assertTrue(message.contains("(2, 0, 0)"))
        assertTrue(message.contains("no name"))
        assertFalse(exported)
    }

    @Test
    fun `dispatch of save reports duplicate names and both positions`() {
        ChestNamer.setName(blockAt(Material.CHEST, 0, 0, 0), "Shop")
        ChestNamer.setName(blockAt(Material.CHEST, 2, 0, 0), "shop")
        var exported = false
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(
            plugin,
            region(0, 0, 0, 2, 0, 0),
            exporter = { _, _ -> exported = true },
        )
        player.isOp = true

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner save")

        val message = plainMessage()
        assertTrue(message.contains("Shop"))
        assertTrue(message.contains("(0, 0, 0)"))
        assertTrue(message.contains("(2, 0, 0)"))
        assertFalse(exported)
    }

    @Test
    fun `dispatch of save reports an unusable output path`() {
        ChestNamer.setName(blockAt(Material.CHEST, 0, 0, 0), "Shop")
        val plugin = MockCommandAPIPlugin.load()
        UiDesignerCommand(
            plugin = plugin,
            selectionProvider = { region(0, 0, 0, 0, 0, 0) },
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
    fun `console save does not export`() {
        blockAt(Material.CHEST, 0, 0, 0)
        var exported = false
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(
            plugin,
            region(0, 0, 0, 0, 0, 0),
            exporter = { _, _ -> exported = true },
        )

        CommandAPITestUtilities.assertCommandSucceeds(server.consoleSender, "uidesigner save")

        assertFalse(exported)
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
    fun `help lists the subcommands`() {
        val plugin = MockCommandAPIPlugin.load()
        registeredCommand(plugin)

        CommandAPITestUtilities.assertCommandSucceeds(player, "uidesigner help")

        val message = plainMessage()
        assertTrue(message.contains("save"))
        assertTrue(message.contains("reload"))
        assertTrue(message.contains("help"))
        assertTrue(message.contains("chest-edit"))
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
            selectionProvider = { region },
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
            selectionProvider = { region },
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

    private fun clippedHalfChest(
        x: Int,
        z: Int,
        material: Material = Material.CHEST,
        type: ChestData.Type = ChestData.Type.LEFT,
    ): Block =
        blockAt(material, x, 0, z).apply {
            blockData =
                ChestDataMock(material).apply {
                    this.type = type
                    facing = BlockFace.NORTH
                }
        }

    private fun region(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Region =
        Region(
            Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble()),
            Location(world, maxX.toDouble(), maxY.toDouble(), maxZ.toDouble()),
        )

    private companion object {
        val DEFAULT_OUTPUT: Path = Path.of("/tmp/uidesigner-test/design.json")
    }
}
