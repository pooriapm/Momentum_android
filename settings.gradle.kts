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
        // Cafe Bazaar's Poolakey billing library is published on JitPack.
        maven("https://jitpack.io")
    }
}

rootProject.name = "Momentum"
include(":app")
