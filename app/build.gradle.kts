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
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
            pickFirst("**/*.so")
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs(layout.buildDirectory.dir("gdx-natives/lib"))
        }
    }
}

dependencies {

    /* ===================== Core ===================== */
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    /* ===================== Compose UI ===================== */
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    /* ===================== Permissions ===================== */
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    /* ===================== CameraX ===================== */
    val cameraxVersion = "1.3.0-rc01"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    /* ===================== Cryptography ===================== */
    implementation("com.google.crypto.tink:tink-android:1.8.0")

    /* ===================== Database (Encrypted) ===================== */
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    /* ===================== Networking / P2P ===================== */
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    /* ===================== WebRTC ===================== */
    implementation("com.getstream:stream-webrtc-android:1.0.3")

    /* ===================== Media ===================== */
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    /* ===================== Firebase (Dumb Transport ONLY) ===================== */
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth")      // anonymous signIn
    implementation("com.google.firebase:firebase-firestore") // dumb storage

    /* ===================== libGDX ===================== */
    implementation("com.badlogicgames.gdx:gdx:1.12.1")
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.12.1")

    val gdxVersion = "1.12.1"
    listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64").forEach {
        runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$it")
    }

    /* ===================== Coroutines ===================== */
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    /* ===================== Background ===================== */
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}

/* ===================== libGDX Native Copy ===================== */

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

tasks.withType<com.android.build.gradle.tasks.MergeSourceSetFolders>().configureEach {
    if (name.contains("JniLibFolders")) dependsOn("copyAndroidNatives")
}
tasks.withType<JavaCompile>().configureEach { dependsOn("copyAndroidNatives") }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("copyAndroidNatives")
}
