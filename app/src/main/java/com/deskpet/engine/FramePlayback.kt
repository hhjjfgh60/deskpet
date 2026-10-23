package com.deskpet.engine

/** Byte-bounded LRU. Eviction drops references; displayed bitmaps are never recycled. */
internal class FrameCache<K, V>(private val maxBytes: Int, private val weight: (V) -> Int) {
    private val entries = LinkedHashMap<K, V>(16, 0.75f, true)
    private var bytes = 0
    operator fun get(key: K): V? = entries[key]
    fun put(key: K, value: V) {
        entries.remove(key)?.let { bytes -= weight(it) }
        entries[key] = value
        bytes += weight(value)
        while (bytes > maxBytes && entries.isNotEmpty()) {
            val iterator = entries.entries.iterator()
            bytes -= weight(iterator.next().value)
            iterator.remove()
        }
    }
    fun clear() { entries.clear(); bytes = 0 }
}

internal data class FramePhase(val frame: Int, val completedLoops: Long, val remainingMs: Long)

/** Samples a monotonic timeline so slow rendering doesn't accumulate timing drift. */
internal fun framePhase(durationsMs: List<Long>, elapsedMs: Long): FramePhase {
    require(durationsMs.isNotEmpty() && durationsMs.all { it > 0 })
    val cycle = durationsMs.sum()
    val elapsed = elapsedMs.coerceAtLeast(0)
    var phase = elapsed % cycle
    durationsMs.forEachIndexed { index, duration ->
        if (phase < duration) return FramePhase(index, elapsed / cycle, duration - phase)
        phase -= duration
    }
    error("Invalid frame timeline")
}
