package com.deskpet.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class CodexPetConfig(
    val id: String,
    val displayName: String,
    val description: String = "",
    val spritesheetPath: String,
    val schema_version: String? = null,
    val states: List<AnimationState>? = null,
    val columns: Int = 8,
    val rows: Int = 9
)

@Serializable
data class AnimationState(
    val name: String,
    val row: Int,
    val frames: Int,
    val frameDurationsMs: List<Long> = emptyList()
)


/** Shared by the parser and tests. Legacy 8x9 pets keep their defaults. */
fun CodexPetConfig.validateLayout() {
    require(columns in 1..32 && rows in 1..32) { "Invalid atlas grid" }
    states?.forEach { state ->
        require(state.row in 0 until rows && state.frames in 1..columns) { "Invalid animation cells" }
        require(state.frameDurationsMs.isEmpty() ||
            (state.frameDurationsMs.size == state.frames && state.frameDurationsMs.all { it in 16L..10_000L })) {
            "Each animation frame needs a valid duration"
        }
    }
}

fun AnimationState.durationMs(frameIndex: Int, fallback: Long): Long =
    frameDurationsMs.getOrNull(Math.floorMod(frameIndex, frames.coerceAtLeast(1))) ?: fallback

/** Integer cell geometry; independent of Android for exact regression tests. */
data class AtlasCell(val left: Int, val top: Int, val width: Int, val height: Int)

fun atlasCell(row: Int, column: Int, width: Int, height: Int, columns: Int, rows: Int): AtlasCell {
    require(columns > 0 && rows > 0 && width > 0 && height > 0)
    require(width % columns == 0 && height % rows == 0) { "Atlas dimensions do not match grid" }
    require(row in 0 until rows && column in 0 until columns)
    val cellWidth = width / columns
    val cellHeight = height / rows
    return AtlasCell(column * cellWidth, row * cellHeight, cellWidth, cellHeight)
}
