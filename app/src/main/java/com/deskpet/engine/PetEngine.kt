package com.deskpet.engine

import android.graphics.Bitmap
import com.deskpet.data.model.CodexPetConfig
import com.deskpet.data.model.PetAnimationState
import com.deskpet.data.model.PetInfo
import com.deskpet.data.model.PetType

class PetEngine(
    private val spritesheetParser: SpritesheetParser,
    private val gifRenderer: GifRenderer
) {
    companion object {
        const val SPRITESHEET_COLS = 8
        const val SPRITESHEET_ROWS = 9
        const val DEFAULT_PET_WIDTH = 192
        const val DEFAULT_PET_HEIGHT = 208
    }


    sealed class PetLoader {
        data class CodexPet(
            val config: CodexPetConfig,
            val spritesheetPath: String
        ) : PetLoader()

        data class Gif(val filePath: String) : PetLoader()
    }

    fun createLoader(petInfo: PetInfo): PetLoader? {
        return when (petInfo.type) {
            PetType.CODEX_PET -> {
                val config = petInfo.petJsonPath?.let {
                    spritesheetParser.parse(it)
                } ?: return null

                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(petInfo.spritesheetPath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                    bounds.outWidth % config.columns != 0 || bounds.outHeight % config.rows != 0) return null
                PetLoader.CodexPet(config, petInfo.spritesheetPath)
            }
            PetType.GIF -> {
                if (gifRenderer.load(petInfo.spritesheetPath)) {
                    PetLoader.Gif(petInfo.spritesheetPath)
                } else {
                    null
                }
            }
        }
    }

    fun prepare(loader: PetLoader) {
        if (loader is PetLoader.CodexPet) spritesheetParser.preload(loader.config, loader.spritesheetPath)
    }

    fun getCurrentFrame(
        loader: PetLoader,
        state: PetAnimationState,
        frameIndex: Int
    ): Bitmap? {
        return when (loader) {
            is PetLoader.CodexPet -> {
                spritesheetParser.extractFrame(
                    loader.config,
                    loader.spritesheetPath,
                    state,
                    frameIndex
                )
            }
            is PetLoader.Gif -> {
                gifRenderer.getFrame()
            }
        }
    }

    fun getFrameCount(loader: PetLoader, state: PetAnimationState): Int {
        return when (loader) {
            is PetLoader.CodexPet -> spritesheetParser.getFrameCount(loader.config, state)
            is PetLoader.Gif -> 1  // GIF 不分帧，每帧独立
        }
    }

    fun getFrameDurationMs(loader: PetLoader, state: PetAnimationState, frameIndex: Int): Long =
        when (loader) {
            is PetLoader.CodexPet -> spritesheetParser.getFrameDurationMs(loader.config, state, frameIndex)
            is PetLoader.Gif -> 32L // GIF renderer owns frame timing.
        }

    fun getPetSize(loader: PetLoader): Pair<Int, Int> {
        return when (loader) {
            is PetLoader.CodexPet -> {
                // spritesheet 8x9 grid, cell = spritesheetW/8 x spritesheetH/9
                // But we need to read actual dimensions
                val spritesheet = java.io.File(loader.spritesheetPath)
                if (!spritesheet.exists()) return Pair(DEFAULT_PET_WIDTH, DEFAULT_PET_HEIGHT)
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(loader.spritesheetPath, options)
                val w = options.outWidth / loader.config.columns
                val h = options.outHeight / loader.config.rows
                if (w <= 0 || h <= 0) Pair(DEFAULT_PET_WIDTH, DEFAULT_PET_HEIGHT) else Pair(w, h)
            }
            is PetLoader.Gif -> Pair(gifRenderer.width, gifRenderer.height)
        }
    }
}

