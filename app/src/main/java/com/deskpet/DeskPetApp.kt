package com.deskpet

import android.app.Application
import com.deskpet.data.PetRepository
import com.deskpet.data.PetStore

class DeskPetApp : Application() {
    val persistenceScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    val petRepository: PetRepository by lazy { PetRepository(PetStore(this)) }
}


