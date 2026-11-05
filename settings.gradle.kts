pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LazierPub"

include(":shared")
include(":server")

// The Android module needs the Android SDK. Set LAZIER_SKIP_ANDROID=true to
// build only the server, for example inside the Docker image.
if (System.getenv("LAZIER_SKIP_ANDROID") != "true") {
    include(":androidApp")
}
