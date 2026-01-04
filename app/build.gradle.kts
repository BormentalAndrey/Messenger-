// app/build.gradle.kts

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

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        ndk {
            abiFilters.addAll(
                listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            )
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("my-release-key.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            val enableSign = (System.getenv("KEYSTORE_PASSWORD") ?: "").isNotEmpty()
            if (enableSign) {
                signingConfig = signingConfigs.getByName("release")
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {}
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
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
        }
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += "**/libwg.so"
            pickFirsts += "**/libwg-go.so"
            pickFirsts += "**/libwg-quick.so"
            pickFirsts += "**/libjingle_peerconnection_so.so"
            pickFirsts += "**/libandroidx.graphics.path.so"
            pickFirsts += "**/libdatastore_shared_counter.so"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.1")

    implementation("com.wireguard.android:tunnel:1.0.20230706")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Compose BOM — управляет версиями всех Compose-компонентов
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation") // версия берётся из BOM
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Coil для обложек
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Media3 (ExoPlayer) — полностью рабочий плеер с уведомлениями
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1") // обязательно для MediaSession и уведомлений
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Coroutines — исправлено: добавлен правильный group, версии согласованы
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    // Другие
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("io.getstream:stream-webrtc-android:1.2.0")
}
