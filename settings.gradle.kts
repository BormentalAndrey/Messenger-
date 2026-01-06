pluginManagement {
    repositories {
        google()
        mavenCentral() // ОБЯЗАТЕЛЬНО здесь для KSP
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://getstream.io/maven") }
        maven { url = uri("https://jitpack.io") }
        mavenCentral()
    }
}

rootProject.name = "MessengerP2P"
include(":app")

