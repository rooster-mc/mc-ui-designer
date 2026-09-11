package dev.cypdashuhn.uidesigner.commands

import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import net.kyori.adventure.text.Component
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

// Slightly above the 4.5 survival block reach: intentional tooling reach for op design work.
private const val REACH = 5

class ChestEditCommand(
    private val plugin: JavaPlugin,
    private val targetResolver: (Player) -> Block? = { it.getTargetBlockExact(REACH) },
) {
    sealed interface Outcome {
        data class Named(
            val name: String
        ) : Outcome

        data object Cleared : Outcome

        data object NotAChest : Outcome
    }

    fun register() {
        CommandAPICommand("chest-edit")
            .withPermission("uidesigner.chest-edit")
            .withArguments(GreedyStringArgument("name"))
            .executesPlayer(
                PlayerCommandExecutor { player, args ->
                    val rawName = args["name"] as String
                    when (val outcome = apply(targetResolver(player), rawName)) {
                        is Outcome.Named ->
                            player.sendMessage(
                                Component.text("Named this chest \"${outcome.name}\".")
                            )
                        Outcome.Cleared ->
                            player.sendMessage(Component.text("Cleared this chest's name."))
                        Outcome.NotAChest ->
                            player.sendMessage(Component.text("Look at a chest to name it."))
                    }
                },
            ).register(plugin)
    }

    // CommandAPI flattens subcommands after a parent argument, so `/chest-edit clear`
    // cannot be a subcommand; "clear" is reserved case-insensitively as a sentinel
    // name instead, which makes a literal name "clear" (any casing) unreachable.
    // Blank input is treated as clear so it never stores an empty custom name.
    fun apply(target: Block?, rawName: String): Outcome {
        if (target == null || !ChestNamer.isChest(target)) return Outcome.NotAChest
        if (rawName.isBlank() || rawName.equals("clear", ignoreCase = true)) {
            ChestNamer.clear(target)
            return Outcome.Cleared
        }
        ChestNamer.setName(target, rawName)
        return Outcome.Named(rawName)
    }
}
