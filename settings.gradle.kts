// Файл: /settings.gradle.kts

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
        // КРИТИЧЕСКИ ВАЖНО для WebRTC:
        maven { url = uri("https://getstream.io/maven") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Messenger-P2P"
include(":app")

