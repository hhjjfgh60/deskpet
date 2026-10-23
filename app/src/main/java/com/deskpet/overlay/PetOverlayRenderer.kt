package com.deskpet.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.deskpet.engine.AnimationController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Measured canvas pixels, not an assumed dp size or a list-card thumbnail. */
data class OverlayPixelInfo(val source: IntSize, val displayed: IntSize, val density: Float)
object OverlayDiagnostics {
    private val state = MutableStateFlow<OverlayPixelInfo?>(null)
    val pixels = state.asStateFlow()
    fun update(value: OverlayPixelInfo?) { state.value = value }
}

@Composable
fun PetOverlayRenderer(anim: AnimationController, gestureModifier: Modifier, modifier: Modifier = Modifier) {
    val frame by anim.currentFrame.collectAsState()
    val w by anim.petWidth.collectAsState()
    val h by anim.petHeight.collectAsState()
    val density = LocalDensity.current
    var measured by remember { mutableStateOf(IntSize.Zero) }
    val source = frame?.let { IntSize(it.width, it.height) }
    LaunchedEffect(source, measured, density.density) {
        if (source != null && measured.width > 0 && measured.height > 0) {
            OverlayDiagnostics.update(OverlayPixelInfo(source, measured, density.density))
        }
    }
    DisposableEffect(Unit) { onDispose { OverlayDiagnostics.update(null) } }
    // w/h are physical pixels. Convert to dp exactly once for Compose layout.
    Box(modifier.then(gestureModifier)
        .size(with(density) { w.toDp() }, with(density) { h.toDp() })
        .onSizeChanged { measured = it }, Alignment.TopStart) {
        Canvas(Modifier.fillMaxSize()) {
            frame?.let { bitmap ->
                val target = IntSize(size.width.toInt(), size.height.toInt())
                val native = IntSize(bitmap.width, bitmap.height)
                drawImage(
                    image = bitmap.asImageBitmap(),
                    srcSize = native,
                    dstSize = target,
                    filterQuality = when {
                        native == target -> FilterQuality.None // Exact one source pixel per screen pixel.
                        target.width < native.width || target.height < native.height -> FilterQuality.Medium
                        else -> FilterQuality.Low // Smooth enlargement, never described as added detail.
                    }
                )
            }
        }
    }
}
