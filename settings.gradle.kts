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
include(":app")

/* =========================
   TERMUX MODULES
   ========================= */
include(":termux-shared")
include(":terminal-view")
include(":termux-app")
