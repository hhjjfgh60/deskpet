package com.deskpet.engine

import com.deskpet.data.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DragonAtlasTest {
    private fun config() = Json.decodeFromString<CodexPetConfig>(
        File("src/main/assets/pets/gpt-dragon/pet.json").readText()
    )

    @Test fun `dragon cells use eleven rows and preserve the direction rows`() {
        val config = config()
        config.validateLayout()
        assertEquals(11, config.rows)
        assertEquals(8, config.columns)
        assertEquals(AtlasCell(0, 1664, 192, 208), atlasCell(8, 0, 1536, 2288, config.columns, config.rows))
        assertEquals(AtlasCell(1344, 2080, 192, 208), atlasCell(10, 7, 1536, 2288, config.columns, config.rows))
        assertEquals(57, config.states!!.sumOf { it.frames })
        assertTrue(config.states!!.all { it.row < 9 }) // Look directions are poses, not idle frames.
    }

    @Test fun `idle blink durations are not replaced by a uniform timer`() {
        val config = config()
        val parser = SpritesheetParser()
        assertEquals(listOf(280L, 110L, 110L, 140L, 140L, 320L),
            (0..5).map { parser.getFrameDurationMs(config, PetAnimationState.IDLE, it) })
        assertEquals(280L, parser.getFrameDurationMs(config, PetAnimationState.IDLE, 6))
        assertEquals(4, parser.getFrameCount(config, PetAnimationState.WAVING))
        assertEquals(280L, parser.getFrameDurationMs(config, PetAnimationState.WAVING, 3))
    }

    @Test fun `legacy pets keep nine rows and existing timing`() {
        val legacy = CodexPetConfig("old", "Old", spritesheetPath = "spritesheet.webp")
        legacy.validateLayout()
        assertEquals(AtlasCell(0, 1664, 192, 208), atlasCell(8, 0, 1536, 1872, legacy.columns, legacy.rows))
        assertEquals(500L, SpritesheetParser().getFrameDurationMs(legacy, PetAnimationState.IDLE, 0))
        assertEquals(200L, SpritesheetParser().getFrameDurationMs(legacy, PetAnimationState.WAVING, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched frame durations are rejected`() {
        config().copy(states = listOf(AnimationState("idle", 0, 6, listOf(100L)))).validateLayout()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `out of bounds states are rejected`() {
        config().copy(states = listOf(AnimationState("idle", 11, 6))).validateLayout()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `images not divisible by configured grid are rejected`() {
        atlasCell(0, 0, 1536, 2288, 8, 9)
    }
}
