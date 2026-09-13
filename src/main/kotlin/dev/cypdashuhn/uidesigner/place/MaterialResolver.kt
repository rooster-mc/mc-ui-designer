package dev.cypdashuhn.uidesigner.place

import org.bukkit.Material

object MaterialResolver {
    fun isKnown(id: String): Boolean = Material.matchMaterial(id) != null
}
