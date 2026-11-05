plugins {
    alias(libs.plugins.android.application)
    // AGP 9 compiles Kotlin itself, so no kotlin.android plugin.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.lazier.pub"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.lazier.pub"
        minSdk = 24          // Readium 3.4 requires API 24 or later
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        // Readium requires core library desugaring.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // The contract with the server: DTOs and JSON configuration.
    implementation(project(":shared"))

    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coroutines.android)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Readium 3.4.0 - EPUB, PDF and audiobook support.
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
}
