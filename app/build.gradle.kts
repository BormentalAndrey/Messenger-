// Файл: app/build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.kakdela.p2p"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kakdela.p2p"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Исправление для Room: указываем аргумент для KSP
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        create("release") {
            // Файл ключа должен быть в папке app/
            storeFile = file("my-release-key.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "debug_pass"
            keyAlias = System.getenv("KEY_ALIAS") ?: "debug_alias"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "debug_pass"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Используем ту же подпись, что и для релиза (нужно для Firebase App Check)
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Версия 1.5.11 совместима с Kotlin 1.9.23
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Исключаем дубликаты мета-данных из разных библиотек
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // Базовые библиотеки Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Jetpack Compose (BOM управляет версиями автоматически)
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Coil для загрузки фото (неоновые аватарки и медиа)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Room (Локальная база данных)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Firebase (Основной бэкенд и Auth)
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    // Библиотека для работы с JSON (Исправляет ошибку Unresolved reference: Gson)
    implementation("com.google.code.gson:gson:2.10.1")

    // VPN: Официальная библиотека WireGuard (Исправляет ошибку Could not find)
    implementation("com.wireguard.android:tunnel:1.1.0")

    // WebRTC для звонков
    implementation("io.getstream:stream-webrtc-android:1.2.0")

    // Coroutines (поддержка Play Services)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // DataStore (Хранение настроек пользователя)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager (Фоновые задачи)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Тестирование
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

