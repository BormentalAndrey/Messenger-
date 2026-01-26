import java.util.Properties
import java.io.FileInputStream

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

/* ------------------------- Android ------------------------- */
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
        vectorDrawables.useSupportLibrary = true

        // Настройка схемы базы данных Room
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        // Поддерживаемые архитектуры (важно для Termux и GDX)
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        // API Key для Gemini
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${System.getenv("GEMINI_API_KEY") ?: localProperties.getProperty("GEMINI_API_KEY", "")}\""
        )
    }

    /* ------------------------- Signing ------------------------- */
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

    /* ------------------------- Build Types ------------------------- */
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false // Termux ресурсы могут быть удалены шринкером, лучше выключить или аккуратно настроить
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

    /* ------------------------- Java / Kotlin ------------------------- */
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    /* ------------------------- Compose ------------------------- */
    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    /* ------------------------- Packaging (Критично для JNI) ------------------------- */
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
                "META-INF/library_release.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
        jniLibs {
            // Разрешает конфликты, выбирая первый найденный .so файл
            pickFirsts += "**/*.so"
            // Включает старый механизм упаковки, необходимый для загрузки .so файлов напрямую из APK
            // Это решает проблему "UnsatisfiedLinkError" для GDX и Termux
            useLegacyPackaging = true 
        }
    }

    sourceSets {
        getByName("main") {
            // Указываем стандартную папку.
            // Gradle сам подтянет natives из зависимостей runtimeOnly.
            jniLibs.srcDirs("src/main/jniLibs") 
        }
    }
}

/* ------------------------- Versions ------------------------- */
val roomVersion = "2.6.1"
val gdxVersion = "1.12.1"
val media3Version = "1.4.1"
val webrtcVersion = "1.0.32006"
val okhttpVersion = "4.12.0"
val tinkVersion = "1.15.0"
val coilVersion = "2.6.0"
val poiVersion = "5.2.5"
val guavaVersion = "33.2.1-android" // Обновлено до версии из 1-го файла для совместимости с Termux

/* ------------------------- Dependencies ------------------------- */
dependencies {

    // --- TERMUX MODULES & LIBS ---
    // Локальные модули
    implementation(project(":termux-shared"))
    implementation(project(":terminal-view"))
    
    // Библиотека эмулятора терминала (содержит .so файлы для PTY)
    implementation("com.github.termux:terminal-emulator:0.118.0")

    // --- Core Libraries ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Preferences (нужен для Termux настроек)
    implementation("androidx.preference:preference-ktx:1.2.1") 
    // Guava (нужен для Termux Shared)
    implementation("com.google.guava:guava:$guavaVersion")

    // --- Dependency Injection (Koin) ---
    implementation("io.insert-koin:koin-android:3.5.0")
    implementation("io.insert-koin:koin-androidx-workmanager:3.5.0")

    // --- UI Components ---
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // --- Jetpack Compose ---
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // --- Image Loading (Coil) ---
    implementation("io.coil-kt:coil-compose:$coilVersion")

    // --- Documents (POI & PDF) ---
    implementation("org.apache.poi:poi-ooxml:$poiVersion")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // --- Utils ---
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.39")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // --- Room Database ---
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // --- Media & Player (ExoPlayer) ---
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")

    // --- WebRTC ---
    // Включаем обе зависимости для надежности, так как в разных версиях кода использовались разные
    implementation("org.webrtc:google-webrtc:$webrtcVersion")
    implementation("io.getstream:stream-webrtc-android:1.2.0")
    implementation("io.getstream:stream-webrtc-android-compose:1.1.2")

    // --- Security (Tink) ---
    implementation("com.google.crypto.tink:tink-android:$tinkVersion")

    // --- Network (Retrofit & OkHttp) ---
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")

    // --- Game Engine (libGDX) ---
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    
    // ВАЖНО: runtimeOnly зависимости автоматически извлекают .so файлы в APK.
    // Это заменяет устаревшую задачу "copyAndroidNatives" и чинит краш "Couldn't load shared library".
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")

    // --- Tests ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.10")

    // --- Java 8+ API Support ---
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
}
