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
        google() // Обязательно первым для Tink и Firebase
        mavenCentral()
        maven { url = uri("https://getstream.io/maven") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.google.com") } 
    }
}

rootProject.name = "MessengerP2P"
include(":app")

