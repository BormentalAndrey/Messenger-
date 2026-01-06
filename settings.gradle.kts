dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google() // Обязательно первым для AndroidX, Tink, Firebase

        // Добавляем зеркала Maven Central (Aliyun — основное, быстрое и надёжное в CI)
        maven { url = uri("https://maven.aliyun.com/repository/public") }     // включает central + другие
        maven { url = uri("https://maven.aliyun.com/repository/central") }    // чистый central (опционально)

        // Дополнительные fallback-зеркала (на случай проблем с Aliyun)
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-central/") }
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/central/") }

        mavenCentral() // Оставляем как fallback (Gradle будет пробовать только если предыдущие не сработают)

        maven { url = uri("https://getstream.io/maven") }
        maven { url = uri("https://jitpack.io") }
        // maven { url = uri("https://maven.google.com") } // дублирует google(), можно убрать
    }
}
