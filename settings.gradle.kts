pluginManagement {
    repositories {
        google() // Плагины Android всегда тут
        // Зеркало для плагинов (если Google тормозит или блокирует)
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        
        // Основные скоростные зеркала
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        
        // Fallback зеркала
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-central/") }
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }

        mavenCentral()

        // Специфические репозитории
        maven { url = uri("https://getstream.io/maven") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MessengerP2P"
include(":app")

