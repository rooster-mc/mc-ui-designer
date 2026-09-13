package dev.cypdashuhn.uidesigner.commands

import dev.cypdashuhn.uidesigner.naming.ChestNamer
import dev.cypdashuhn.uidesigner.util.Messages
import dev.rooster.commands.argOrNull
import dev.rooster.commands.commandapi.command
import dev.rooster.commands.onExecute
import dev.rooster.commands.playerOrNull
import dev.rooster.commands.types.greedyString
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

        data object BlankName : Outcome

        data object NoTarget : Outcome

        data object NotAChest : Outcome
    }

    fun register() {
        command("chest-edit") {
            onExecute {
                val player = playerOrNull ?: return@onExecute
                player.sendMessage(usageMessage())
            }
            greedyString("name").onExecute {
                val player = playerOrNull ?: return@onExecute
                val rawName = argOrNull<String>("name") ?: return@onExecute
                player.sendMessage(outcomeMessage(apply(targetResolver(player), rawName)))
            }
        }.register(plugin)
    }

    private fun outcomeMessage(outcome: Outcome) =
        when (outcome) {
            is Outcome.Named -> namedMessage(outcome.name)
            Outcome.BlankName -> usageMessage()
            Outcome.NoTarget -> noTargetMessage()
            Outcome.NotAChest -> notAChestMessage()
        }

    fun apply(target: Block?, rawName: String): Outcome {
        if (target == null) return Outcome.NoTarget
        if (!ChestNamer.isChest(target)) return Outcome.NotAChest
        val name = rawName.trim()
        if (name.isEmpty()) return Outcome.BlankName
        ChestNamer.setName(target, name)
        return Outcome.Named(name)
    }
}

internal fun usageMessage(): Component =
    Messages.styled(
        Messages.infoColor,
        "Usage: /chest-edit <name> - name the chest you are looking at.",
    )

internal fun namedMessage(name: String): Component =
    Messages.styled(Messages.successColor, "Named this chest \"$name\".")

internal fun noTargetMessage(): Component =
    Messages.styled(Messages.errorColor, "Not looking at a chest (or it is out of reach).")

internal fun notAChestMessage(): Component =
    Messages.styled(Messages.errorColor, "That block is not a chest.")
