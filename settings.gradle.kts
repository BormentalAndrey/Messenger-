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
    // Гарантирует, что настройки репозиториев здесь являются единственным источником правды
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

        // JitPack для специфических библиотек (например, Termux или кастомные форки)
        maven { 
            url = uri("https://jitpack.io") 
            // Добавляем фильтр, чтобы не опрашивать JitPack по каждой зависимости (ускоряет билд)
            content {
                includeGroup("com.github.termux")
                includeGroup("com.github.kakdela-p2p") // пример вашего будущего репозитория
            }
        }

        // Зеркало для повышения стабильности загрузки в случае сбоев основных серверов
        maven { 
            url = uri("https://maven.aliyun.com/repository/public") 
            mavenContent {
                releasesOnly()
            }
        }
    }
}

// Название проекта
rootProject.name = "MessengerP2P"

// Подключаемые модули
include(":app")
