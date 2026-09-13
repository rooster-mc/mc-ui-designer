package dev.cypdashuhn.uidesigner.commands

import dev.cypdashuhn.uidesigner.capture.ChestCapture
import dev.cypdashuhn.uidesigner.capture.ClippedHalf
import dev.cypdashuhn.uidesigner.capture.DoubleChestGrouper
import dev.cypdashuhn.uidesigner.config.ReloadResult
import dev.cypdashuhn.uidesigner.config.UiDesignerConfig
import dev.cypdashuhn.uidesigner.export.ChestStatus
import dev.cypdashuhn.uidesigner.export.DuplicateNameGroup
import dev.cypdashuhn.uidesigner.export.InvalidDesignException
import dev.cypdashuhn.uidesigner.export.JsonExporter
import dev.cypdashuhn.uidesigner.export.JsonImporter
import dev.cypdashuhn.uidesigner.export.Reconcile
import dev.cypdashuhn.uidesigner.export.ReconcileReport
import dev.cypdashuhn.uidesigner.export.UiChest
import dev.cypdashuhn.uidesigner.export.UnjoinableReason
import dev.cypdashuhn.uidesigner.export.requiredPosition
import dev.cypdashuhn.uidesigner.export.validateForExport
import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.cypdashuhn.uidesigner.place.ChestSyncer
import dev.cypdashuhn.uidesigner.place.MaterialResolver
import dev.cypdashuhn.uidesigner.place.PlacementResult
import dev.cypdashuhn.uidesigner.place.ScaffoldPlacer
import dev.cypdashuhn.uidesigner.util.Messages
import dev.rooster.commands.argOrNull
import dev.rooster.commands.commandapi.command
import dev.rooster.commands.onExecute
import dev.rooster.commands.optional
import dev.rooster.commands.playerOrNull
import dev.rooster.commands.suggestStrings
import dev.rooster.commands.then
import dev.rooster.commands.types.greedyString
import dev.rooster.commands.types.literal
import dev.rooster.region.BlockPos
import dev.rooster.region.Region
import kotlinx.serialization.SerializationException
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.IOException
import java.nio.file.Path

class UiDesignerCommand(
    private val plugin: JavaPlugin,
    private val selectionProvider: (Player) -> Region?,
    private val configProvider: () -> UiDesignerConfig,
    private val reloadAction: () -> ReloadResult,
    private val exporter: (List<UiChest>, Path) -> Unit = JsonExporter::export,
    private val importer: (Path) -> List<UiChest> =
        { JsonImporter.read(it, MaterialResolver::isKnown) },
    private val placer: (Player, List<UiChest>) -> PlacementResult = ScaffoldPlacer::place,
    private val syncer: (Region, ReconcileReport) -> ChestSyncer.SyncResult =
        { region, report -> ChestSyncer.apply(region, report) },
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

        data class ClippedChests(
            val clipped: List<ClippedHalf>,
        ) : SaveOutcome

        data class InvalidNames(
            val unnamed: List<BlockPos>,
            val duplicates: List<DuplicateNameGroup>,
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

    sealed interface ScaffoldOutcome {
        data class Placed(
            val chests: Int,
            val file: Path,
        ) : ScaffoldOutcome

        data object NoTarget : ScaffoldOutcome

        data class ParseFailure(
            val file: Path,
            val reason: String?,
        ) : ScaffoldOutcome

        data class Obstructed(
            val blocked: Int,
            val first: BlockPos,
            val firstIsPlayer: Boolean,
        ) : ScaffoldOutcome

        data class IoFailure(
            val file: Path?,
            val reason: String?,
        ) : ScaffoldOutcome
    }

    sealed interface StatusOutcome {
        data class Reported(
            val file: Path,
            val report: ReconcileReport,
        ) : StatusOutcome

        data class ParseFailure(
            val file: Path,
            val reason: String?,
        ) : StatusOutcome

        data class IoFailure(
            val file: Path?,
            val reason: String?,
        ) : StatusOutcome

        data object NoSelection : StatusOutcome

        data object NoChests : StatusOutcome

        data class ClippedChests(
            val clipped: List<ClippedHalf>,
        ) : StatusOutcome
    }

    sealed interface SyncOutcome {
        data class Synced(
            val file: Path,
            val report: ReconcileReport,
            val result: ChestSyncer.SyncResult,
        ) : SyncOutcome

        data class ParseFailure(
            val file: Path,
            val reason: String?,
        ) : SyncOutcome

        data class IoFailure(
            val file: Path?,
            val reason: String?,
        ) : SyncOutcome

        data object NoSelection : SyncOutcome

        data object NoChests : SyncOutcome

        data class ClippedChests(
            val clipped: List<ClippedHalf>,
        ) : SyncOutcome
    }

    private sealed interface CaptureOutcome {
        data object NoSelection : CaptureOutcome

        data object NoChests : CaptureOutcome

        data class Clipped(
            val clipped: List<ClippedHalf>,
        ) : CaptureOutcome

        data class Captured(
            val region: Region,
            val chests: List<UiChest>,
        ) : CaptureOutcome
    }

    private sealed interface DesignRead {
        data class Ok(
            val file: Path,
            val chests: List<UiChest>,
        ) : DesignRead

        data class ParseFailure(
            val file: Path,
            val reason: String?,
        ) : DesignRead

        data class IoFailure(
            val file: Path?,
            val reason: String?,
        ) : DesignRead
    }

    fun register() {
        command("uidesigner") {
            onExecute { sender.sendMessage(helpMessage()) }
            literal("save").onExecute {
                val player = playerOrNull ?: return@onExecute
                player.sendMessage(saveMessage(save(player)))
            }
            literal("scaffold")
                .onExecute {
                    val player = playerOrNull ?: return@onExecute
                    player.sendMessage(scaffoldMessage(scaffold(player, null)))
                }.then {
                    greedyString("file")
                        .optional()
                        .suggestStrings { configProvider().jsonFiles() }
                        .onExecute {
                            val player = playerOrNull ?: return@onExecute
                            player.sendMessage(scaffoldMessage(scaffold(player, argOrNull("file"))))
                        }
                }
            literal("status")
                .onExecute {
                    val player = playerOrNull ?: return@onExecute
                    player.sendMessage(statusMessage(status(player, null)))
                }.then {
                    greedyString("file")
                        .optional()
                        .suggestStrings { configProvider().jsonFiles() }
                        .onExecute {
                            val player = playerOrNull ?: return@onExecute
                            player.sendMessage(statusMessage(status(player, argOrNull("file"))))
                        }
                }
            literal("sync")
                .onExecute {
                    val player = playerOrNull ?: return@onExecute
                    player.sendMessage(syncMessage(sync(player, null)))
                }.then {
                    greedyString("file")
                        .optional()
                        .suggestStrings { configProvider().jsonFiles() }
                        .onExecute {
                            val player = playerOrNull ?: return@onExecute
                            player.sendMessage(syncMessage(sync(player, argOrNull("file"))))
                        }
                }
            literal("reload").onExecute { sender.sendMessage(reloadMessage(reload())) }
            literal("help").onExecute { sender.sendMessage(helpMessage()) }
        }.withAliases("uid")
            .register(plugin)
    }

    fun save(player: Player): SaveOutcome =
        when (val captured = capture(player)) {
            CaptureOutcome.NoSelection -> SaveOutcome.NoSelection
            CaptureOutcome.NoChests -> SaveOutcome.NoChests
            is CaptureOutcome.Clipped -> SaveOutcome.ClippedChests(captured.clipped)
            is CaptureOutcome.Captured -> exportDesign(captured.chests)
        }

    private fun exportDesign(chests: List<UiChest>): SaveOutcome {
        val validation = validateForExport(chests)
        if (!validation.isValid) {
            return SaveOutcome.InvalidNames(validation.unnamed, validation.duplicates)
        }
        val outputFile =
            try {
                configProvider().outputFile
            } catch (e: Exception) {
                return SaveOutcome.InvalidOutputFile(e.message)
            }
        return try {
            exporter(chests, outputFile)
            SaveOutcome.Exported(chests.size, outputFile)
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

    fun scaffold(player: Player, rawFile: String?): ScaffoldOutcome =
        when (val design = readDesign(rawFile)) {
            is DesignRead.ParseFailure -> ScaffoldOutcome.ParseFailure(design.file, design.reason)
            is DesignRead.IoFailure -> ScaffoldOutcome.IoFailure(design.file, design.reason)
            is DesignRead.Ok ->
                when (val result = placer(player, design.chests)) {
                    is PlacementResult.Placed -> ScaffoldOutcome.Placed(result.chests, design.file)
                    is PlacementResult.Obstructed ->
                        ScaffoldOutcome.Obstructed(
                            result.blocked,
                            result.first,
                            result.firstIsPlayer
                        )
                    PlacementResult.NoTarget -> ScaffoldOutcome.NoTarget
                }
        }

    fun status(player: Player, rawFile: String?): StatusOutcome =
        when (val design = readDesign(rawFile)) {
            is DesignRead.ParseFailure -> StatusOutcome.ParseFailure(design.file, design.reason)
            is DesignRead.IoFailure -> StatusOutcome.IoFailure(design.file, design.reason)
            is DesignRead.Ok ->
                when (val captured = capture(player)) {
                    CaptureOutcome.NoSelection -> StatusOutcome.NoSelection
                    CaptureOutcome.NoChests -> StatusOutcome.NoChests
                    is CaptureOutcome.Clipped -> StatusOutcome.ClippedChests(captured.clipped)
                    is CaptureOutcome.Captured ->
                        StatusOutcome.Reported(
                            design.file,
                            Reconcile.reconcile(design.chests, captured.chests),
                        )
                }
        }

    fun sync(player: Player, rawFile: String?): SyncOutcome =
        when (val design = readDesign(rawFile)) {
            is DesignRead.ParseFailure -> SyncOutcome.ParseFailure(design.file, design.reason)
            is DesignRead.IoFailure -> SyncOutcome.IoFailure(design.file, design.reason)
            is DesignRead.Ok ->
                when (val captured = capture(player)) {
                    CaptureOutcome.NoSelection -> SyncOutcome.NoSelection
                    CaptureOutcome.NoChests -> SyncOutcome.NoChests
                    is CaptureOutcome.Clipped -> SyncOutcome.ClippedChests(captured.clipped)
                    is CaptureOutcome.Captured -> {
                        val report = Reconcile.reconcile(design.chests, captured.chests)
                        SyncOutcome.Synced(
                            design.file,
                            report,
                            syncer(captured.region, report),
                        )
                    }
                }
        }

    private fun capture(player: Player): CaptureOutcome {
        val selection =
            ChestCapture.capture(selectionProvider, player) ?: return CaptureOutcome.NoSelection
        if (selection.contents.isEmpty()) return CaptureOutcome.NoChests
        val grouped = DoubleChestGrouper.group(selection.region, selection.contents)
        if (grouped.clipped.isNotEmpty()) return CaptureOutcome.Clipped(grouped.clipped)
        val named =
            grouped.chests.map { chest ->
                val name =
                    chest.position?.let { position -> nameAt(selection.region, position) }.orEmpty()
                chest.copy(name = name)
            }
        return CaptureOutcome.Captured(selection.region, named)
    }

    private fun readDesign(rawFile: String?): DesignRead {
        val file =
            try {
                resolveDesignFile(rawFile)
            } catch (e: Exception) {
                return DesignRead.IoFailure(null, e.message)
            }
        val chests =
            try {
                importer(file)
            } catch (e: IOException) {
                return DesignRead.IoFailure(file, reasonWithoutPath(e.message, file))
            } catch (e: InvalidDesignException) {
                return DesignRead.ParseFailure(file, e.detail)
            } catch (e: SerializationException) {
                return DesignRead.ParseFailure(file, MALFORMED_DESIGN)
            } catch (e: Exception) {
                return DesignRead.ParseFailure(file, e.message)
            }
        return DesignRead.Ok(file, chests)
    }

    private fun resolveDesignFile(rawFile: String?): Path {
        val config = configProvider()
        if (rawFile.isNullOrBlank()) return config.outputFile
        return config.resolvePath(rawFile)
    }

    private fun nameAt(region: Region, position: BlockPos): String? =
        ChestNamer.nameOf(region.blockAt(position))

    private fun saveMessage(outcome: SaveOutcome): Component =
        when (outcome) {
            is SaveOutcome.Exported -> saveSuccessMessage(outcome.chests, outcome.outputFile)
            SaveOutcome.NoSelection -> noSelectionMessage()
            SaveOutcome.NoChests -> noChestsMessage()
            is SaveOutcome.WriteFailed -> writeFailedMessage(outcome.outputFile, outcome.reason)
            is SaveOutcome.ClippedChests -> clippedChestsMessage(outcome.clipped)
            is SaveOutcome.InvalidNames -> invalidNamesMessage(outcome.unnamed, outcome.duplicates)
            is SaveOutcome.InvalidOutputFile -> invalidOutputFileMessage(outcome.reason)
        }

    private fun scaffoldMessage(outcome: ScaffoldOutcome): Component =
        when (outcome) {
            is ScaffoldOutcome.Placed -> scaffoldSuccessMessage(outcome.chests, outcome.file)
            ScaffoldOutcome.NoTarget -> scaffoldNoTargetMessage()
            is ScaffoldOutcome.ParseFailure ->
                scaffoldParseFailureMessage(outcome.file, outcome.reason)
            is ScaffoldOutcome.Obstructed ->
                scaffoldObstructedMessage(outcome.blocked, outcome.first, outcome.firstIsPlayer)
            is ScaffoldOutcome.IoFailure -> scaffoldIoFailureMessage(outcome.file, outcome.reason)
        }

    private fun statusMessage(outcome: StatusOutcome): Component =
        when (outcome) {
            is StatusOutcome.Reported -> statusReportMessage(outcome.file, outcome.report)
            is StatusOutcome.ParseFailure ->
                scaffoldParseFailureMessage(outcome.file, outcome.reason)
            is StatusOutcome.IoFailure -> scaffoldIoFailureMessage(outcome.file, outcome.reason)
            StatusOutcome.NoSelection -> noSelectionMessage()
            StatusOutcome.NoChests -> noChestsMessage()
            is StatusOutcome.ClippedChests -> clippedChestsMessage(outcome.clipped)
        }

    private fun syncMessage(outcome: SyncOutcome): Component =
        when (outcome) {
            is SyncOutcome.Synced ->
                syncReportMessage(outcome.file, outcome.report, outcome.result)
            is SyncOutcome.ParseFailure ->
                scaffoldParseFailureMessage(outcome.file, outcome.reason)
            is SyncOutcome.IoFailure -> scaffoldIoFailureMessage(outcome.file, outcome.reason)
            SyncOutcome.NoSelection -> noSelectionMessage()
            SyncOutcome.NoChests -> noChestsMessage()
            is SyncOutcome.ClippedChests -> clippedChestsMessage(outcome.clipped)
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
        "/uidesigner scaffold [file] - place empty chests in a row across your view " +
        "(default file: the configured output-file).\n" +
        "  The row starts at the block you are looking at; aim at open space to use " +
        "the block in front of you.\n" +
        "/uidesigner status [file] - report how the selected chests drift from the design " +
        "(default file: the configured output-file).\n" +
        "/uidesigner sync [file] - apply the design's contents to the selected chests.\n" +
        "/uidesigner reload - reload config.yml.\n" +
        "/chest-edit <name> - name the chest you are looking at.\n" +
        "/uidesigner help - show this help."

private const val WRITE_FAILURE_HINT = "check that the output folder exists and is writable"
private const val RELOAD_FAILURE_HINT = "check config.yml and the server log"
private const val INVALID_OUTPUT_HINT = "check the output-file setting"
private const val SCAFFOLD_IO_HINT = "check the file exists and is readable"
private const val SCAFFOLD_PARSE_HINT = "check the file matches docs/data-format.md"
private const val MALFORMED_DESIGN = "the file is not valid JSON"

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

internal fun clippedChestsMessage(clipped: List<ClippedHalf>): Component {
    val first = clipped.first()
    val rest = clipped.size - 1
    val others =
        if (rest == 0) {
            ""
        } else {
            " $rest more ${if (rest == 1) "chest is" else "chests are"} also clipped."
        }
    val detail =
        if (first.partnerInsideSelection) {
            "the chest at ${first.position.coords()} is half of a double chest whose other half " +
                "at ${first.partner.coords()} is inside your selection but was not captured " +
                "because its chunk is not loaded; move near ${first.partner.coords()} to load " +
                "that chunk and try again, or shrink the selection so neither half of this " +
                "double chest is selected."
        } else {
            "the chest at ${first.position.coords()} is half of a double chest whose other half " +
                "at ${first.partner.coords()} is outside your selection. " +
                "Expand the selection to include both halves."
        }
    return Messages.styled(Messages.errorColor, "Cannot export: $detail$others")
}

private fun BlockPos.coords(): String = "($x, $y, $z)"

private fun reasonWithoutPath(reason: String?, file: Path): String? =
    reason
        ?.removePrefix(file.toString())
        ?.removePrefix(":")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

internal fun invalidNamesMessage(
    unnamed: List<BlockPos>,
    duplicates: List<DuplicateNameGroup>,
): Component {
    val body = listOfNotNull(unnamedClause(unnamed), duplicateClause(duplicates)).joinToString(" ")
    return Messages.styled(Messages.errorColor, "Cannot export: $body")
}

private fun unnamedClause(positions: List<BlockPos>): String? {
    if (positions.isEmpty()) return null
    val count = positions.size
    val subject = if (count == 1) "1 chest has" else "$count chests have"
    val pronoun = if (count == 1) "it" else "them"
    val listed = positions.joinToString(", ") { it.coords() }
    return "$subject no name. Name $pronoun with /chest-edit <name>: $listed."
}

private fun duplicateClause(duplicates: List<DuplicateNameGroup>): String? {
    if (duplicates.isEmpty()) return null
    val listed =
        duplicates.joinToString("; ") { group ->
            group.entries.joinToString(", ") { "\"${it.name}\" at ${it.position.coords()}" }
        }
    return "Chest names must be unique (compared ignoring case and surrounding spaces). " +
        "Duplicates: $listed."
}

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

internal fun scaffoldSuccessMessage(chests: Int, file: Path): Component {
    val noun = if (chests == 1) "chest design" else "chest designs"
    return Messages.styled(
        Messages.successColor,
        "Scaffolded $chests $noun from $file (a double chest counts once).",
    )
}

internal fun scaffoldNoTargetMessage(): Component =
    Messages.styled(
        Messages.errorColor,
        "Cannot scaffold: no world or usable target block. Aim at open space to place " +
            "the row in front of you.",
    )

internal fun scaffoldParseFailureMessage(file: Path, reason: String?): Component {
    val detail =
        Messages.withTrailingPeriod(Messages.reasonOrDefault(reason, SCAFFOLD_PARSE_HINT))
    return Messages.styled(Messages.errorColor, "Invalid design in $file: $detail")
}

internal fun scaffoldObstructedMessage(
    blocked: Int,
    first: BlockPos,
    firstIsPlayer: Boolean,
): Component {
    val subject = if (blocked == 1) "1 target block is" else "$blocked target blocks are"
    val recovery =
        if (firstIsPlayer) {
            "the first at ${first.coords()} is inside your body, so step aside"
        } else {
            "the first at ${first.coords()} is not replaceable, so clear it or aim at " +
                "open space"
        }
    return Messages.styled(
        Messages.errorColor,
        "Cannot scaffold: $subject obstructed; $recovery and try again; nothing placed.",
    )
}

internal fun scaffoldIoFailureMessage(file: Path?, reason: String?): Component {
    val hint = if (file == null) INVALID_OUTPUT_HINT else SCAFFOLD_IO_HINT
    val target = file?.let { "the design file $it" } ?: "the design file"
    val detail = Messages.withTrailingPeriod(Messages.reasonOrDefault(reason, hint))
    return Messages.styled(Messages.errorColor, "Could not read $target: $detail")
}

internal fun statusReportMessage(file: Path, report: ReconcileReport): Component {
    val color = if (report.isInSync) Messages.successColor else Messages.infoColor
    return Messages.styled(color, reportLines(file, report).joinToString("\n"))
}

internal fun syncReportMessage(
    file: Path,
    report: ReconcileReport,
    result: ChestSyncer.SyncResult,
): Component {
    val noun = if (result.applied == 1) "chest design" else "chest designs"
    val lines = mutableListOf("Synced ${result.applied} matched $noun from $file.")
    lines += reportLines(file, report)
    if (result.contentsSkipped > 0) {
        lines +=
            "  ${result.contentsSkipped} chest(s) had structure drift; contents left untouched."
    }
    val color = if (report.isInSync) Messages.successColor else Messages.infoColor
    return Messages.styled(color, lines.joinToString("\n"))
}

private fun reportLines(file: Path, report: ReconcileReport): List<String> {
    val header =
        "Reconciled $file: ${report.inSync.size} in-sync, ${report.updated.size} updated, " +
            "${report.missing.size} missing, ${report.orphans.size} orphan, " +
            "${report.unjoinable.size} unjoinable."
    return buildList {
        add(header)
        report.updated.forEach { add(updatedLine(it)) }
        report.missing.forEach { add("  missing \"${it.file.name}\"") }
        report.orphans.forEach {
            add("  orphan \"${it.world.name}\" at ${it.world.requiredPosition().coords()}")
        }
        report.unjoinable.forEach { add(unjoinableLine(it)) }
    }
}

private fun updatedLine(updated: ChestStatus.Updated): String {
    val drift = updated.drift
    val parts =
        buildList {
            if (drift.rowsDiffer) {
                add(
                    "structure drift (world ${updated.world.rows} rows, file ${updated.file.rows} rows)"
                )
            }
            if (drift.nameDiffer) add("name differs (world \"${updated.world.name}\")")
            if (drift.differences.isNotEmpty()) add("${drift.differences.size} slot(s) differ")
        }
    val position = updated.world.requiredPosition().coords()
    return "  updated \"${updated.file.name}\" at $position: ${parts.joinToString("; ")}."
}

private fun unjoinableLine(unjoinable: ChestStatus.Unjoinable): String {
    val detail =
        when (unjoinable.reason) {
            UnjoinableReason.UNNAMED -> "no name"
            UnjoinableReason.DUPLICATE -> "duplicate name \"${unjoinable.world.name}\""
        }
    val position = unjoinable.world.requiredPosition().coords()
    return "  unjoinable chest at $position: $detail."
}
