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
        // ДОБАВЛЯЕМ JitPack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Messenger-"
include(":app")
