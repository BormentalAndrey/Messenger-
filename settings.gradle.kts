pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Строго запрещаем объявления репозиториев в build.gradle
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        // при необходимости добавьте JitPack:
        // maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Messenger-"
include(":app")
