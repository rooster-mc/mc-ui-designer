package dev.cypdashuhn.uidesigner.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import java.nio.file.Path

object Messages {
    const val PREFIX = "[UiDesigner] "

    private val prefixColor: TextColor = NamedTextColor.AQUA
    private val successColor: TextColor = NamedTextColor.GREEN
    private val errorColor: TextColor = NamedTextColor.RED
    private val infoColor: TextColor = NamedTextColor.YELLOW

    private const val HELP_TEXT =
        "UiDesigner commands:\n" +
            "/uidesigner save (op) - export the selected chest designs to JSON.\n" +
            "/uidesigner reload (op) - reload config.yml.\n" +
            "/chest-edit <name> (op) - name or clear the chest you are looking at.\n" +
            "/uidesigner help - show this help."

    fun help(): Component = styled(infoColor, HELP_TEXT)

    fun saveSuccess(chests: Int, outputFile: Path): Component {
        val noun = if (chests == 1) "chest design" else "chest designs"
        return styled(
            successColor,
            "Exported $chests $noun to $outputFile (a double chest counts once).",
        )
    }

    fun noSelection(): Component =
        styled(errorColor, "No WorldEdit selection. Select a region first.")

    fun noChests(): Component =
        styled(
            errorColor,
            "The selection contains no chests. Place chests inside the selected region " +
                "(loaded chunks only).",
        )

    fun writeFailed(outputFile: Path?, reason: String): Component {
        val target = outputFile?.let { " to $it" } ?: ""
        return styled(
            errorColor,
            "Could not write the export$target: ${withTrailingPeriod(reason)}",
        )
    }

    fun invalidOutputFile(reason: String): Component =
        styled(
            errorColor,
            "The configured output path is not usable: ${withTrailingPeriod(reason)}",
        )

    fun reloadSuccess(outputFile: Path): Component =
        styled(successColor, "Reloaded config.yml. Output file: $outputFile.")

    fun reloadUsingDefaults(outputFile: Path): Component =
        styled(
            infoColor,
            "config.yml could not be read; using defaults. Output file: $outputFile.",
        )

    fun reloadInvalidOutput(outputFile: Path): Component =
        styled(
            infoColor,
            "output-file in config.yml is not a valid path; using defaults. " +
                "Output file: $outputFile.",
        )

    fun reloadFailed(reason: String): Component =
        styled(errorColor, "Could not reload config.yml: ${withTrailingPeriod(reason)}")

    fun chestEditNamed(name: String): Component =
        styled(successColor, "Named this chest \"$name\".")

    fun chestEditCleared(): Component = styled(successColor, "Cleared this chest's name.")

    fun chestEditNothingToClear(): Component = styled(infoColor, "This chest has no name.")

    fun chestEditNoTarget(): Component =
        styled(errorColor, "Not looking at a chest (or it is out of reach).")

    fun chestEditNotAChest(): Component = styled(errorColor, "That block is not a chest.")

    fun chestEditUsage(): Component =
        styled(
            infoColor,
            "Usage: /chest-edit <name> - name the chest you are looking at, " +
                "or /chest-edit clear to remove the name.",
        )

    fun noPermission(node: String): Component =
        styled(errorColor, "You do not have permission to use this command ($node).")

    private fun styled(color: TextColor, body: String): Component =
        styled(color, Component.text(body))

    private fun styled(color: TextColor, body: Component): Component =
        Component
            .text()
            .color(color)
            .append(Component.text(PREFIX).color(prefixColor))
            .append(body)
            .build()

    private fun withTrailingPeriod(text: String): String = text.trim().trimEnd('.') + "."
}
