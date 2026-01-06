pluginManagement {
    repositories {
        google()
        mavenCentral() // обязательно для KSP и других плагинов
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Запрет добавлять репозитории в build.gradle.kts
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    
    repositories {
        google()                                 // Google Maven
        mavenCentral()                            // Central Maven
        maven("https://jitpack.io")              // Для JitPack зависимостей (если будут)
        maven("https://getstream.io/maven")      // Для WebRTC Stream SDK
        maven("https://maven.aliyun.com/repository/public") // зеркало Maven (опционально)
    }
}

rootProject.name = "MessengerP2P"
include(":app")
