package com.deskpet.data

import android.content.Context
import android.util.Log
import com.deskpet.data.model.CodexPetConfig
import com.deskpet.data.model.validateLayout
import com.deskpet.data.model.PetInfo
import com.deskpet.data.model.PetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

object SamplePetLoader {

    private const val TAG = "SamplePetLoader"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadSamplePets(context: Context, petRepository: PetRepository) {
        val allPets = petRepository.getAllPets().first()
        // Add newly bundled pets on upgrade without resetting an existing selection.

        withContext(Dispatchers.IO) {
            try {
                val assetsPetsDir = "pets"
                val assetList = context.assets.list(assetsPetsDir) ?: return@withContext
                val internalPetsDir = File(context.filesDir, "pets").also { it.mkdirs() }

                for (petName in assetList.sortedBy { if (it == "gpt-dragon") 0 else 1 }) {
                    if (allPets.any { it.id == petName }) continue
                    try {
                        val srcDir = "$assetsPetsDir/$petName"
                        val destDir = File(internalPetsDir, petName)

                        val petJsonContent = context.assets.open("$srcDir/pet.json")
                            .bufferedReader().readText()
                        val config = json.decodeFromString<CodexPetConfig>(petJsonContent)
                        config.validateLayout()

                        destDir.mkdirs()
                        val petFiles = context.assets.list(srcDir) ?: continue
                        for (fileName in petFiles) {
                            context.assets.open("$srcDir/$fileName").use { input ->
                                File(destDir, fileName).outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }

                        val spritesheetFile = File(destDir, "spritesheet.webp")
                            .takeIf { it.exists() }
                            ?: File(destDir, "spritesheet.png").takeIf { it.exists() }
                            ?: continue

                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(spritesheetFile.absolutePath, bounds)
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                            bounds.outWidth % config.columns != 0 || bounds.outHeight % config.rows != 0) {
                            Log.e(TAG, "Invalid bundled atlas: $petName")
                            continue
                        }

                        val petInfo = PetInfo(
                            id = petName,
                            type = PetType.CODEX_PET,
                            displayName = config.displayName,
                            petJsonPath = File(destDir, "pet.json").absolutePath,
                            spritesheetPath = spritesheetFile.absolutePath,
                            sourceId = config.id
                        )
                        petRepository.addPetDirect(petInfo)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load sample pet: $petName", e)
                    }
                }

                val loaded = petRepository.getAllPets().first()
                if (petRepository.getActivePetId().first().isNullOrEmpty()) {
                    (loaded.find { it.id == "gpt-dragon" } ?: loaded.firstOrNull())
                        ?.let { petRepository.setActivePet(it.id) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load sample pets", e)
            }
        }
    }
}

