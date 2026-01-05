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
        // Добавлено для WebRTC (ТЗ п. 8)
        maven { url = uri("https://getstream.io/maven") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Messenger-"
include(":app")

