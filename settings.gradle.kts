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
        // WebRTC иногда требует jcenter для старых зависимостей, 
        // но сначала пробуем без него.
    }
}

rootProject.name = "Messenger-"
include(":app")

