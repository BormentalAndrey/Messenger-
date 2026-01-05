// Файл: /app/build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.kakdela.p2p"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kakdela.p2p"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        ndk {
            // Поддержка всех актуальных архитектур для WebRTC и libGDX
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("my-release-key.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Для P2P лучше сначала собрать без сжатия, чтобы не "отрезало" нативные методы
            if ((System.getenv("KEYSTORE_PASSWORD") ?: "").isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/kotlinx-coroutines-core.kotlin_module"
            pickFirst("**/*.so") // Важно для конфликтов WebRTC/libGDX
        }
    }
}

dependencies {
    /* ===================== Core & UI ===================== */
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    /* ===================== Cryptography (E2EE) ===================== */
    // Соответствие ТЗ п.10.1
    implementation("com.google.crypto.tink:tink-android:1.8.0")

    /* ===================== Database (E2EE Storage) ===================== */
    // Соответствие ТЗ п.5.4
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    /* ===================== Networking / P2P ===================== */
    implementation("com.google.code.gson:gson:2.10.1")
    
    // WebRTC для P2P звонков (ТЗ п.8)
    // Используем актуальную версию, совместимую с Maven GetStream
    implementation("io.getstream:stream-webrtc-android:1.1.2")

    /* ===================== Firebase (Dumb Transport) ===================== */
    // Используется ТОЛЬКО как Relay (п.3.1 ТЗ)
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    /* ===================== libGDX ===================== */
    val gdxVersion = "1.12.1"
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64").forEach {
        runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$it")
    }

    /* ===================== Misc ===================== */
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
}

/* ===================== libGDX Native Copy Task ===================== */
// Эта часть гарантирует, что нативные библиотеки (.so) будут встроены в APK
tasks.register<Copy>("copyAndroidNatives") {
    val gdxVersion = "1.12.1"
    val platforms = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

    platforms.forEach { platform ->
        val jarConfiguration = configurations.detachedConfiguration(
            dependencies.create("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$platform")
        )
        from(jarConfiguration.map { zipTree(it) }) {
            include("*.so")
            into("lib/$platform")
        }
    }
    into(layout.buildDirectory.dir("gdx-natives"))
}

// Связываем копирование нативов с процессом компиляции
android.applicationVariants.all {
    val variantName = name.replaceFirstChar { it.uppercase() }
    tasks.named("merge${variantName}JniLibFolders") {
        dependsOn("copyAndroidNatives")
    }
}

