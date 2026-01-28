import java.util.Properties
import java.io.FileInputStream
import org.gradle.api.tasks.Copy

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    kotlin("kapt")
}

/* ------------------------- Local properties ------------------------- */
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

/* ------------------------- Versions ------------------------- */
val roomVersion = "2.6.1"
val gdxVersion = "1.12.1"
val media3Version = "1.4.1"
val okhttpVersion = "4.12.0"
val tinkVersion = "1.15.0"
val coilVersion = "2.6.0"
val poiVersion = "5.2.5"
val guavaVersion = "33.2.1-android"

/* ------------------------- GDX Native Copy Task ------------------------- */
val copyAndroidNatives = tasks.register<Copy>("copyAndroidNatives") {
    val platforms = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    into(layout.projectDirectory.dir("src/main/jniLibs"))

    platforms.forEach { platform ->
        val cfg = configurations.detachedConfiguration(
            dependencies.create(
                "com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$platform"
            )
        )
        from(cfg.map { zipTree(it) }) {
            include("**/*.so")
            into(platform)
        }
    }
}

/* ------------------------- Android ------------------------- */
android {
    namespace = "com.kakdela.p2p"
    compileSdk = 35

    // Рекомендуемая версия NDK для работы с современными нативными библиотеками (Llama)
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.kakdela.p2p"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        // Настройка фильтров архитектур
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        // Параметры компиляции C++ для LlamaBridge
        externalNativeBuild {
            cmake {
                // Оптимизация -O3 критична для скорости работы ИИ
                cppFlags("-std=c++17", "-O3", "-frtti", "-fexceptions")
                arguments("-DANDROID_STL=c++_shared")
            }
        }

        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${System.getenv("GEMINI_API_KEY")
                ?: localProperties.getProperty("GEMINI_API_KEY", "")}\""
        )
    }

    // Указываем путь к вашему CMakeLists.txt
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("my-release-key.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: localProperties.getProperty("RELEASE_STORE_PASSWORD", "")
            keyAlias = System.getenv("KEY_ALIAS")
                ?: localProperties.getProperty("RELEASE_KEY_ALIAS", "")
            keyPassword = System.getenv("KEY_PASSWORD")
                ?: localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
            enableV1Signing = true
            enableV2Signing = true
        }

        getByName("debug") {
            storeFile = file("testkey_untrusted.jks")
            keyAlias = "alias"
            storePassword = "xrj45yWGLbsO7W0v"
            keyPassword = "xrj45yWGLbsO7W0v"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/NOTICE*",
                "META-INF/LICENSE*",
                "META-INF/kotlinx-coroutines-core.kotlin_module",
                "META-INF/tink/**",
                "META-INF/library_release.kotlin_module"
            )
        }
        jniLibs {
            pickFirsts += "**/*.so"
            // Включаем поддержку старой упаковки для совместимости с библиотеками GDX и JNI
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            // Совмещаем нативные библиотеки GDX и наши скомпилированные CMake
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

/* ------------------------- Hook natives ------------------------- */
tasks.whenTaskAdded {
    if (name.contains("merge", true) && name.contains("JniLibFolders", true)) {
        dependsOn(copyAndroidNatives)
    }
}

/* ------------------------- Dependencies ------------------------- */
dependencies {

    // TERMUX
    implementation(project(":termux-shared"))
    implementation(project(":terminal-view"))
    implementation(project(":terminal-emulator"))

    // Android Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.guava:guava:$guavaVersion")

    // Koin
    implementation("io.insert-koin:koin-android:3.5.0")
    implementation("io.insert-koin:koin-androidx-workmanager:3.5.0")

    // UI
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Images
    implementation("io.coil-kt:coil-compose:$coilVersion")

    // Docs
    implementation("org.apache.poi:poi-ooxml:$poiVersion")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Utils
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.39")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // JSON Processing
    implementation("org.json:json:20231013")

    // Room
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Media
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")

    // WebRTC
    implementation("io.getstream:stream-webrtc-android:1.2.0")
    implementation("io.getstream:stream-webrtc-android-compose:1.1.2")

    // Security
    implementation("com.google.crypto.tink:tink-android:$tinkVersion")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")

    // libGDX
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.10")

    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
}
