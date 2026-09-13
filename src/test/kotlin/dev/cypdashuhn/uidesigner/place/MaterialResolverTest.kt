package dev.cypdashuhn.uidesigner.place

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaterialResolverTest {
    @Test
    fun `a namespaced exported id is known`() {
        assertTrue(MaterialResolver.isKnown("minecraft:stone"))
    }

    @Test
    fun `a legacy id is known`() {
        assertTrue(MaterialResolver.isKnown("STONE"))
    }

    @Test
    fun `an unknown or blank id is not known`() {
        assertFalse(MaterialResolver.isKnown("minecraft:not_a_thing"))
        assertFalse(MaterialResolver.isKnown(""))
    }
}
