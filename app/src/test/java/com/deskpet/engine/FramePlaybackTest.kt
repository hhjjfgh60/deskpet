package com.deskpet.engine

import org.junit.Assert.*
import org.junit.Test

class FramePlaybackTest {
    private val idle = listOf(280L, 110L, 110L, 140L, 140L, 320L)
    @Test fun `source blink durations and boundaries are preserved`() {
        assertEquals(FramePhase(0, 0, 280), framePhase(idle, 0))
        assertEquals(FramePhase(1, 0, 110), framePhase(idle, 280))
        assertEquals(FramePhase(2, 0, 110), framePhase(idle, 390))
        assertEquals(FramePhase(0, 1, 280), framePhase(idle, 1100))
    }
    @Test fun `late presentation catches up instead of delaying every later frame`() {
        assertEquals(FramePhase(2, 0, 80), framePhase(idle, 420))
        assertEquals(FramePhase(0, 2, 280), framePhase(idle, 2200))
    }
    @Test fun `cache reuses decoded frames and evicts least recently used within budget`() {
        val cache = FrameCache<String, String>(4) { it.length }
        cache.put("idle-0", "aa"); cache.put("idle-1", "bb")
        assertEquals("aa", cache["idle-0"])
        cache.put("wave-0", "cc")
        assertNull(cache["idle-1"])
        assertEquals("aa", cache["idle-0"])
        assertEquals("cc", cache["wave-0"])
        cache.clear(); assertNull(cache["idle-0"])
    }
}
