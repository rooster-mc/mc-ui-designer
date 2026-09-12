package dev.cypdashuhn.uidesigner.commands

import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.cypdashuhn.uidesigner.util.Messages
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.SafeSuggestions
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

// Slightly above the 4.5 survival block reach: intentional tooling reach for op design work.
private const val REACH = 5

// TODO: Why didnt we use rooster-commands? We want it introduced.
class ChestEditCommand(
    private val plugin: JavaPlugin,
    private val targetResolver: (Player) -> Block? = { it.getTargetBlockExact(REACH) },
) {
    sealed interface Outcome {
        data class Named(
            val name: String
        ) : Outcome

        data object Cleared : Outcome

        data object NothingToClear : Outcome

        data object NoTarget : Outcome

        data object NotAChest : Outcome
    }

    fun register() {
        CommandAPICommand("chest-edit")
            .withArguments(
                GreedyStringArgument("name")
                    // TODO: turn "clear" into a variabl3
                    .replaceSafeSuggestions(SafeSuggestions.suggest("clear"))
                    .setOptional(true),
            ).executesPlayer(
                PlayerCommandExecutor { player, args ->
                    when {
                        !player.hasPermission(PERMISSION) -> {
                            player.sendMessage(Messages.noPermission(PERMISSION))
                        }

                        args["name"] == null -> {
                            player.sendMessage(Messages.chestEditUsage())
                        }

                        else -> {
                            val rawName = args["name"] as String
                            when (val outcome = apply(targetResolver(player), rawName)) {
                                is Outcome.Named -> {
                                    player.sendMessage(Messages.chestEditNamed(outcome.name))
                                }

                                Outcome.Cleared -> {
                                    player.sendMessage(Messages.chestEditCleared())
                                }

                                Outcome.NothingToClear -> {
                                    player.sendMessage(Messages.chestEditNothingToClear())
                                }

                                Outcome.NoTarget -> {
                                    player.sendMessage(Messages.chestEditNoTarget())
                                }

                                Outcome.NotAChest -> {
                                    player.sendMessage(Messages.chestEditNotAChest())
                                }
                            }
                        }
                    }
                },
            ).register(plugin)
    }

    // TODO: This could just be a literal in rooster-commands right, with a command defined AFTER the
    // literal

    // CommandAPI flattens subcommands after a parent argument, so `/chest-edit clear`
    // cannot be a subcommand; "clear" is reserved case-insensitively as a sentinel
    // name instead, which makes a literal name "clear" (any casing) unreachable.
    // Input is trimmed first, so whitespace-padded "clear" cannot smuggle in a name,
    // and blank input is treated as clear so it never stores an empty custom name.
    fun apply(target: Block?, rawName: String): Outcome {
        if (target == null) return Outcome.NoTarget
        if (!ChestNamer.isChest(target)) return Outcome.NotAChest
        val name = rawName.trim()
        if (name.isEmpty() || name.equals("clear", ignoreCase = true)) {
            if (ChestNamer.nameOf(target) == null) return Outcome.NothingToClear
            ChestNamer.clear(target)
            return Outcome.Cleared
        }
        ChestNamer.setName(target, name)
        return Outcome.Named(name)
    }

    // TODO: This goes for both this and every other permission - this is a local tool. we can just
    // ommit permissions as a concept.
    companion object {
        const val PERMISSION = "uidesigner.chest-edit"
    }
}
