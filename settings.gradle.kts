pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // FAIL_ON_PROJECT_REPOS гарантирует, что все репозитории объявлены только здесь
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        
        // Репозиторий для WebRTC Stream SDK
        maven {
            url = uri("https://getstream.io/maven")
        }

        // JitPack для Termux и других сторонних библиотек
        maven { 
            url = uri("https://jitpack.io")
        }

        // Зеркало Maven для стабильности сборки
        maven { 
            url = uri("https://maven.aliyun.com/repository/public")
        }
    }
}

rootProject.name = "MessengerP2P"
include(":app")
