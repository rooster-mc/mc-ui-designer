package dev.cypdashuhn.uidesigner.commands

import dev.cypdashuhn.uidesigner.capture.ChestCapture
import dev.cypdashuhn.uidesigner.capture.DoubleChestGrouper
import dev.cypdashuhn.uidesigner.config.ReloadResult
import dev.cypdashuhn.uidesigner.config.UiDesignerConfig
import dev.cypdashuhn.uidesigner.export.JsonExporter
import dev.cypdashuhn.uidesigner.export.UiChest
import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.cypdashuhn.uidesigner.util.Messages
import dev.jorel.commandapi.executors.CommandExecutor
import dev.rooster.commands.commandapi.command
import dev.rooster.commands.onExecute
import dev.rooster.commands.playerOrNull
import dev.rooster.commands.types.literal
import dev.rooster.region.BlockPos
import dev.rooster.region.Region
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Path

class UiDesignerCommand(
    private val plugin: JavaPlugin,
    private val selectionProvider: (Player) -> Region?,
    private val configProvider: () -> UiDesignerConfig,
    private val reloadAction: () -> ReloadResult,
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
            val reason: String?,
        ) : SaveOutcome

        data class InvalidOutputFile(
            val reason: String?,
        ) : SaveOutcome
    }

    sealed interface ReloadOutcome {
        data class Reloaded(
            val outputFile: Path,
        ) : ReloadOutcome

        data class UsingDefaults(
            val outputFile: Path,
        ) : ReloadOutcome

        data class InvalidOutput(
            val outputFile: Path,
        ) : ReloadOutcome

        data class Failed(
            val reason: String?,
        ) : ReloadOutcome
    }

    fun register() {
        val helpExecutor =
            CommandExecutor { sender, _ -> sender.sendMessage(Messages.help()) }
        command("uidesigner") {
            literal("save").onExecute {
                val player = playerOrNull ?: return@onExecute
                player.sendMessage(saveMessage(save(player)))
            }
            literal("reload").onExecute { sender.sendMessage(reloadMessage(reload())) }
            literal("help").onExecute { sender.sendMessage(Messages.help()) }
        }.executes(helpExecutor)
            .withAliases("uid")
            .register(plugin)
    }

    fun save(player: Player): SaveOutcome {
        val selection =
            ChestCapture.capture(selectionProvider, player) ?: return SaveOutcome.NoSelection
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
                return SaveOutcome.InvalidOutputFile(e.message)
            }
        return try {
            exporter(named, outputFile)
            SaveOutcome.Exported(named.size, outputFile)
        } catch (e: Exception) {
            failure(e, outputFile)
        }
    }

    fun reload(): ReloadOutcome =
        try {
            val result = reloadAction()
            val outputFile = configProvider().outputFile
            when (result) {
                ReloadResult.Reloaded -> ReloadOutcome.Reloaded(outputFile)
                ReloadResult.UsingDefaults -> ReloadOutcome.UsingDefaults(outputFile)
                ReloadResult.InvalidOutput -> ReloadOutcome.InvalidOutput(outputFile)
            }
        } catch (e: Exception) {
            ReloadOutcome.Failed(e.message)
        }

    private fun failure(e: Exception, outputFile: Path?): SaveOutcome.WriteFailed =
        SaveOutcome.WriteFailed(outputFile, e.message)

    private fun nameAt(region: Region, position: BlockPos): String? =
        ChestNamer.nameOf(region.blockAt(position))

    private fun saveMessage(outcome: SaveOutcome): Component =
        when (outcome) {
            is SaveOutcome.Exported -> Messages.saveSuccess(outcome.chests, outcome.outputFile)
            SaveOutcome.NoSelection -> Messages.noSelection()
            SaveOutcome.NoChests -> Messages.noChests()
            is SaveOutcome.WriteFailed -> Messages.writeFailed(outcome.outputFile, outcome.reason)
            is SaveOutcome.InvalidOutputFile -> Messages.invalidOutputFile(outcome.reason)
        }

    private fun reloadMessage(outcome: ReloadOutcome): Component =
        when (outcome) {
            is ReloadOutcome.Reloaded -> Messages.reloadSuccess(outcome.outputFile)
            is ReloadOutcome.UsingDefaults -> Messages.reloadUsingDefaults(outcome.outputFile)
            is ReloadOutcome.InvalidOutput -> Messages.reloadInvalidOutput(outcome.outputFile)
            is ReloadOutcome.Failed -> Messages.reloadFailed(outcome.reason)
        }
}
