import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.deskpet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.deskpet"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1-clarity"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (providers.environmentVariable("DESKPET_SIGNING_STORE_FILE").isPresent) {
            create("fixedPersonal") {
                storeFile = file(providers.environmentVariable("DESKPET_SIGNING_STORE_FILE").get())
                storePassword = providers.environmentVariable("DESKPET_SIGNING_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("DESKPET_SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("DESKPET_SIGNING_KEY_PASSWORD").get()
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("fixedPersonal")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.foundation:foundation")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.9.3")

    // Lifecycle (for LifecycleService)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // SavedState (for SavedStateRegistryOwner in Service)
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Kotlin Serialization (for pet.json)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}


// Do not produce an APK named as a dragon build when the real atlas is absent.
val verifyBundledDragon by tasks.registering {
    val atlas = layout.projectDirectory.file("src/main/assets/pets/gpt-dragon/spritesheet.png")
    inputs.file(atlas)
    doLast {
        val file = atlas.asFile
        check(file.isFile && file.length() == 2602295L) {
            "Dragon atlas missing/incomplete. Run python scripts/prepare_assets.py before building."
        }
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == "58ff74425fa8d1e6cf1359532312d39d60735f9515c00df4633b75390485c843") {
            "Dragon atlas checksum mismatch; see doc/gpt-dragon/RIGHTS.md and scripts/prepare_assets.py"
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyBundledDragon) }
