package dev.cypdashuhn.uidesigner.commands

import dev.cypdashuhn.uidesigner.capture.ChestCapture
import dev.cypdashuhn.uidesigner.capture.DoubleChestGrouper
import dev.cypdashuhn.uidesigner.capture.Region
import dev.cypdashuhn.uidesigner.capture.SelectionSource
import dev.cypdashuhn.uidesigner.config.UiDesignerConfig
import dev.cypdashuhn.uidesigner.export.JsonExporter
import dev.cypdashuhn.uidesigner.model.BlockPos
import dev.cypdashuhn.uidesigner.model.UiChest
import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Path

class UiDesignerCommand(
    private val plugin: JavaPlugin,
    private val selectionSource: SelectionSource,
    private val configProvider: () -> UiDesignerConfig,
    private val reloadAction: () -> Unit,
    private val exporter: (List<UiChest>, Path) -> Unit = JsonExporter::export,
) {
    sealed interface SaveOutcome {
        data class Exported(
            val chests: Int,
            val outputFile: Path,
        ) : SaveOutcome

        data object NoSelection : SaveOutcome

        data object NoChests : SaveOutcome

        data class WriteFailed(
            val outputFile: Path?,
            val reason: String,
        ) : SaveOutcome
    }

    fun register() {
        val reloadExecutor =
            CommandExecutor { sender, _ -> sender.sendMessage(reloadMessage(reload())) }
        val helpExecutor = CommandExecutor { sender, _ -> sender.sendMessage(helpMessage()) }
        CommandAPICommand("uidesigner")
            .withAliases("uid")
            .withSubcommand(
                CommandAPICommand("save")
                    .withPermission("uidesigner.save")
                    .executesPlayer(
                        PlayerCommandExecutor { player, _ ->
                            player.sendMessage(saveMessage(save(player)))
                        },
                    ),
            ).withSubcommand(
                CommandAPICommand("reload")
                    .withPermission("uidesigner.reload")
                    .executes(reloadExecutor),
            ).withSubcommand(
                CommandAPICommand("help").executes(helpExecutor),
            ).executes(helpExecutor)
            .register(plugin)
    }

    fun save(player: Player): SaveOutcome {
        val selection =
            ChestCapture.capture(selectionSource, player) ?: return SaveOutcome.NoSelection
        if (selection.contents.isEmpty()) return SaveOutcome.NoChests
        val grouped = DoubleChestGrouper.group(selection.region, selection.contents)
        val named =
            grouped.map { chest ->
                chest.copy(
                    name = chest.position?.let { position -> nameAt(selection.region, position) },
                )
            }
        val outputFile =
            try {
                configProvider().outputFile
            } catch (e: Exception) {
                return failure(e)
            }
        return try {
            exporter(named, outputFile)
            SaveOutcome.Exported(named.size, outputFile)
        } catch (e: Exception) {
            failure(e, outputFile)
        }
    }

    fun reload(): Path {
        reloadAction()
        return configProvider().outputFile
    }

    private fun failure(e: Exception, outputFile: Path? = null): SaveOutcome.WriteFailed =
        SaveOutcome.WriteFailed(outputFile, e.message ?: e.javaClass.simpleName)

    private fun nameAt(region: Region, position: BlockPos): String? =
        ChestNamer.nameOf(region.blockAt(position))

    private fun saveMessage(outcome: SaveOutcome): Component =
        when (outcome) {
            is SaveOutcome.Exported -> {
                val noun = if (outcome.chests == 1) "design" else "designs"
                Component.text(
                    "Exported ${outcome.chests} chest $noun to ${outcome.outputFile} " +
                        "(a double chest counts once).",
                )
            }
            SaveOutcome.NoSelection ->
                Component.text("No WorldEdit selection. Select a region first.")
            SaveOutcome.NoChests ->
                Component.text(
                    "The selection contains no chests. Place chests inside the selected " +
                        "region (loaded chunks only).",
                )
            is SaveOutcome.WriteFailed -> {
                val target = outcome.outputFile?.let { " to $it" } ?: ""
                Component.text("Could not write the export$target: ${outcome.reason}")
            }
        }

    private fun reloadMessage(outputFile: Path): Component =
        Component.text("Reloaded config.yml. Output file: $outputFile.")

    private fun helpMessage(): Component =
        Component.text(
            "UiDesigner commands:\n" +
                "/uidesigner save - export the selected chest designs to JSON\n" +
                "/uidesigner reload - reload config.yml\n" +
                "/uidesigner help - show this help",
        )
}
