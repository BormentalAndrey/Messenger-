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

        maven {
            url = uri("https://getstream.io/maven")
        }

        maven {
            url = uri("https://jitpack.io")
        }

        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
    }
}

rootProject.name = "MessengerP2P"

/* =========================
   MAIN APPLICATION
   ========================= */
// Оставляем только основной модуль приложения.
// Это предотвратит попытки Gradle анализировать сломанные конфиги Termux.
include(":app")

/* =========================
   TERMUX MODULES (REMOVED)
   ========================= */
// Модули :termux-app, :termux-shared и :terminal-view удалены, 
// так как они несовместимы с текущей версией Gradle и вызывают сбой сборки.
