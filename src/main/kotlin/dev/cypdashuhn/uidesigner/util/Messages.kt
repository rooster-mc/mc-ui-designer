package dev.cypdashuhn.uidesigner.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor

object Messages {
    const val PREFIX = "[UiDesigner] "

    val prefixColor: TextColor = NamedTextColor.AQUA
    val successColor: TextColor = NamedTextColor.GREEN
    val errorColor: TextColor = NamedTextColor.RED
    val infoColor: TextColor = NamedTextColor.YELLOW

    fun styled(color: TextColor, body: String): Component = styled(color, Component.text(body))

    fun styled(color: TextColor, body: Component): Component =
        Component
            .text()
            .color(color)
            .append(Component.text(PREFIX).color(prefixColor))
            .append(body)
            .build()

    fun reasonOrDefault(reason: String?, fallback: String): String =
        reason?.takeIf { it.isNotBlank() } ?: fallback

    fun withTrailingPeriod(text: String): String = text.trim().trimEnd('.') + "."
}
