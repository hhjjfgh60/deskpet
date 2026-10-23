package com.deskpet.engine

import android.graphics.Bitmap
import com.deskpet.data.model.PetAnimationState
import com.deskpet.data.model.PetInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AnimationController(
    private val petEngine: PetEngine,
    private val scope: CoroutineScope
) : IAnimationStateController {
    companion object {
        private const val LOOP_COUNT_BEFORE_IDLE = 2
        private const val MIN_PET_SIZE_PX = 32
        private const val MIN_SCALE = 0.3f
        private const val MAX_SCALE = 5f
    }

    private val _state = MutableStateFlow(PetAnimationState.IDLE)
    override val currentState: StateFlow<PetAnimationState> = _state
    private val _frame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _frame
    private val _w = MutableStateFlow(192)
    val petWidth: StateFlow<Int> = _w
    private val _h = MutableStateFlow(208)
    val petHeight: StateFlow<Int> = _h

    @Volatile private var fi = 0
    private var stateRevision = 0
    private var pc = 0
    private var loader: PetEngine.PetLoader? = null
    private var fj: Job? = null
    private var s = 1f
    private var nativeWidth = 192
    private var nativeHeight = 208
    private var stateStartedNs = System.nanoTime()

    suspend fun load(p: PetInfo): Boolean {
        val prepared = withContext(Dispatchers.IO) {
            petEngine.createLoader(p)?.also { petEngine.prepare(it) }
        } ?: return false
        loader = prepared
        val (w, h) = withContext(Dispatchers.IO) { petEngine.getPetSize(prepared) }
        nativeWidth = w; nativeHeight = h
        _w.value = w; _h.value = h; reset(); return true
    }

    suspend fun reload(p: PetInfo): Boolean { stop(); return load(p) }

    fun start() {
        fj?.cancel()
        stateStartedNs = System.nanoTime()
        fj = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val l = loader ?: break
                val revision = stateRevision
                val state = _state.value
                val started = System.nanoTime()
                val durations = (0 until petEngine.getFrameCount(l, state)).map {
                    petEngine.getFrameDurationMs(l, state, it)
                }
                val phase = framePhase(durations, (started - stateStartedNs) / 1_000_000)
                if (isCodexPet() && state != PetAnimationState.IDLE && phase.completedLoops >= LOOP_COUNT_BEFORE_IDLE) {
                    switchState(PetAnimationState.IDLE)
                    continue
                }
                val bitmap = withContext(Dispatchers.IO) {
                    petEngine.getCurrentFrame(l, state, phase.frame)
                }
                if (revision != stateRevision) continue
                if (bitmap != null) _frame.value = bitmap
                val workMs = (System.nanoTime() - started) / 1_000_000
                delay((phase.remainingMs - workMs).coerceAtLeast(1L))
            }
        }
    }

    fun stop() { fj?.cancel(); fj = null }
    override fun switchState(st: PetAnimationState) { stateRevision++; stateStartedNs = System.nanoTime(); _state.value = st; fi = 0; pc = 0 }
    fun reset() { switchState(PetAnimationState.IDLE) }
    fun setScale(v: Float) { s = v.coerceIn(MIN_SCALE, MAX_SCALE); loader?.let { _w.value = (nativeWidth * s).toInt().coerceAtLeast(MIN_PET_SIZE_PX); _h.value = (nativeHeight * s).toInt().coerceAtLeast(MIN_PET_SIZE_PX) } }
    fun isCodexPet() = loader is PetEngine.PetLoader.CodexPet
}

