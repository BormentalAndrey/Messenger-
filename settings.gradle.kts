pluginManagement {
    repositories {
        google()
        mavenCentral() // KSP и плагины
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") } // RichEditor
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }       // RichEditor
        maven { url = uri("https://getstream.io/maven") } // WebRTC
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "MessengerP2P"
include(":app")
