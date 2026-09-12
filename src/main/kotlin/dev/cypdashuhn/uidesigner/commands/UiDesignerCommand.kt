package dev.cypdashuhn.uidesigner.commands

import dev.cypdashuhn.uidesigner.capture.ChestCapture
import dev.cypdashuhn.uidesigner.capture.DoubleChestGrouper
import dev.cypdashuhn.uidesigner.config.ReloadResult
import dev.cypdashuhn.uidesigner.config.UiDesignerConfig
import dev.cypdashuhn.uidesigner.export.JsonExporter
import dev.cypdashuhn.uidesigner.export.UiChest
import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.cypdashuhn.uidesigner.util.Messages
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
        command("uidesigner") {
            onExecute { sender.sendMessage(helpMessage()) }
            literal("save").onExecute {
                val player = playerOrNull ?: return@onExecute
                player.sendMessage(saveMessage(save(player)))
            }
            literal("reload").onExecute { sender.sendMessage(reloadMessage(reload())) }
            literal("help").onExecute { sender.sendMessage(helpMessage()) }
        }.withAliases("uid")
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
            SaveOutcome.WriteFailed(outputFile, e.message)
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

    private fun nameAt(region: Region, position: BlockPos): String? =
        ChestNamer.nameOf(region.blockAt(position))

    private fun saveMessage(outcome: SaveOutcome): Component =
        when (outcome) {
            is SaveOutcome.Exported -> saveSuccessMessage(outcome.chests, outcome.outputFile)
            SaveOutcome.NoSelection -> noSelectionMessage()
            SaveOutcome.NoChests -> noChestsMessage()
            is SaveOutcome.WriteFailed -> writeFailedMessage(outcome.outputFile, outcome.reason)
            is SaveOutcome.InvalidOutputFile -> invalidOutputFileMessage(outcome.reason)
        }

    private fun reloadMessage(outcome: ReloadOutcome): Component =
        when (outcome) {
            is ReloadOutcome.Reloaded -> reloadSuccessMessage(outcome.outputFile)
            is ReloadOutcome.UsingDefaults -> reloadUsingDefaultsMessage(outcome.outputFile)
            is ReloadOutcome.InvalidOutput -> reloadInvalidOutputMessage(outcome.outputFile)
            is ReloadOutcome.Failed -> reloadFailedMessage(outcome.reason)
        }
}

private const val HELP_TEXT =
    "UiDesigner commands:\n" +
        "/uidesigner save - export the selected chest designs to JSON.\n" +
        "/uidesigner reload - reload config.yml.\n" +
        "/chest-edit <name> - name or clear the chest you are looking at.\n" +
        "/uidesigner help - show this help."

private const val WRITE_FAILURE_HINT = "check that the output folder exists and is writable"
private const val RELOAD_FAILURE_HINT = "check config.yml and the server log"
private const val INVALID_OUTPUT_HINT = "check the output-file setting"

internal fun helpMessage(): Component = Messages.styled(Messages.infoColor, HELP_TEXT)

internal fun saveSuccessMessage(chests: Int, outputFile: Path): Component {
    val noun = if (chests == 1) "chest design" else "chest designs"
    return Messages.styled(
        Messages.successColor,
        "Exported $chests $noun to $outputFile (a double chest counts once).",
    )
}

internal fun noSelectionMessage(): Component =
    Messages.styled(Messages.errorColor, "No WorldEdit selection. Select a region first.")

internal fun noChestsMessage(): Component =
    Messages.styled(
        Messages.errorColor,
        "The selection contains no chests. Place chests inside the selected region " +
            "(loaded chunks only).",
    )

internal fun writeFailedMessage(outputFile: Path?, reason: String?): Component {
    val target = outputFile?.let { " to $it" } ?: ""
    val detail = Messages.withTrailingPeriod(Messages.reasonOrDefault(reason, WRITE_FAILURE_HINT))
    return Messages.styled(Messages.errorColor, "Could not write the export$target: $detail")
}

internal fun invalidOutputFileMessage(reason: String?): Component {
    val detail = Messages.withTrailingPeriod(Messages.reasonOrDefault(reason, INVALID_OUTPUT_HINT))
    return Messages.styled(
        Messages.errorColor,
        "The configured output path is not usable: $detail",
    )
}

internal fun reloadSuccessMessage(outputFile: Path): Component =
    Messages.styled(Messages.successColor, "Reloaded config.yml. Output file: $outputFile.")

internal fun reloadUsingDefaultsMessage(outputFile: Path): Component =
    Messages.styled(
        Messages.infoColor,
        "config.yml could not be read; using defaults. Output file: $outputFile.",
    )

internal fun reloadInvalidOutputMessage(outputFile: Path): Component =
    Messages.styled(
        Messages.infoColor,
        "output-file in config.yml is not a valid path; using defaults. " +
            "Output file: $outputFile.",
    )

internal fun reloadFailedMessage(reason: String?): Component {
    val detail = Messages.withTrailingPeriod(Messages.reasonOrDefault(reason, RELOAD_FAILURE_HINT))
    return Messages.styled(Messages.errorColor, "Could not reload config.yml: $detail")
}
