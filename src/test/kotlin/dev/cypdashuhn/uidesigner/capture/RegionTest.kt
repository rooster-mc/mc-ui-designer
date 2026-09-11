package dev.cypdashuhn.uidesigner.capture

import dev.cypdashuhn.uidesigner.model.BlockPos
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.world.WorldMock

class RegionTest {
    private lateinit var server: ServerMock
    private lateinit var world: WorldMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        world = server.addSimpleWorld("world")
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `inverted corners are normalised into min and max`() {
        val region = Region.of(BlockPos(5, 4, 3), BlockPos(1, 2, 0), world)

        assertEquals(BlockPos(1, 2, 0), region.min)
        assertEquals(BlockPos(5, 4, 3), region.max)
    }

    @Test
    fun `mixed corners are normalised per axis`() {
        val region = Region.of(BlockPos(1, 9, 5), BlockPos(7, 2, 3), world)

        assertEquals(BlockPos(1, 2, 3), region.min)
        assertEquals(BlockPos(7, 9, 5), region.max)
    }
}
