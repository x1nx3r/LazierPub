plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // JVM target today. iOS or web targets can be added without moving the models.
    jvm()
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: consumers need Json to use the codec below.
            api(libs.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
