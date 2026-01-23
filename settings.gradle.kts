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
    // Режим FAIL_ON_PROJECT_REPOS требует, чтобы все репозитории были описаны здесь
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

        // JitPack для сторонних библиотек
        maven { 
            url = uri("https://jitpack.io")
        }

        // Зеркало Maven для стабильности
        maven { 
            url = uri("https://maven.aliyun.com/repository/public")
        }
    }
}

// Название корневого проекта
rootProject.name = "MessengerP2P"

// Подключение модулей
include(":app")

// ПОДКЛЮЧЕНИЕ ЛОКАЛЬНОГО МОДУЛЯ TERMUX
// После распаковки архива CI скриптом, Gradle найдет код в этой папке
include(":termux-library")

// Опционально: если структура внутри архива сложная, 
// можно явно указать путь к директории проекта
project(":termux-library").projectDir = file("termux-library")
