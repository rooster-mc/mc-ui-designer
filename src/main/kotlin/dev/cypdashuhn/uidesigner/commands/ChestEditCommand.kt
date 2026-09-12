package dev.cypdashuhn.uidesigner.commands

import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.cypdashuhn.uidesigner.util.Messages
import dev.jorel.commandapi.executors.CommandExecutor
import dev.rooster.commands.argOrNull
import dev.rooster.commands.commandapi.command
import dev.rooster.commands.onExecute
import dev.rooster.commands.playerOrNull
import dev.rooster.commands.types.greedyString
import dev.rooster.commands.types.literal
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

        data object NothingToClear : Outcome

        data object NoTarget : Outcome

        data object NotAChest : Outcome
    }

    fun register() {
        command("chest-edit") {
            greedyString("name")
                .onExecute {
                    val player = playerOrNull ?: return@onExecute
                    sender.sendMessage(
                        outcomeMessage(apply(targetResolver(player), argOrNull("name") ?: "")),
                    )
                }
            // A CommandTree branches at the root, so "clear" can be a real literal node
            // beside the greedy name instead of a reserved sentinel name; the greedy
            // branch still handles any-casing, blank, and padded input via apply().
            literal("clear").onExecute {
                val player = playerOrNull ?: return@onExecute
                sender.sendMessage(outcomeMessage(apply(targetResolver(player), "clear")))
            }
        }.executes(CommandExecutor { sender, _ -> sender.sendMessage(Messages.chestEditUsage()) })
            .register(plugin)
    }

    private fun outcomeMessage(outcome: Outcome) =
        when (outcome) {
            is Outcome.Named -> Messages.chestEditNamed(outcome.name)
            Outcome.Cleared -> Messages.chestEditCleared()
            Outcome.NothingToClear -> Messages.chestEditNothingToClear()
            Outcome.NoTarget -> Messages.chestEditNoTarget()
            Outcome.NotAChest -> Messages.chestEditNotAChest()
        }

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
}
