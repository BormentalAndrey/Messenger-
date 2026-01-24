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

        // Room schemas
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        // NDK
        ndk {
            abiFilters += listOf(
                "armeabi-v7a",
                "arm64-v8a",
                "x86",
                "x86_64"
            )
        }

        // Gemini API key (local + CI)
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${System.getenv("GEMINI_API_KEY")
                ?: localProperties.getProperty("GEMINI_API_KEY", "")}\""
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

    /* ------------------------- Build types ------------------------- */

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

    /* ------------------------- Packaging ------------------------- */

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/NOTICE*",
                "META-INF/LICENSE*",
                "META-INF/kotlinx-coroutines-core.kotlin_module",
                "META-INF/tink/**"
            )
        }

        jniLibs {
            pickFirsts += "**/*.so"
            useLegacyPackaging = true
        }
    }

    /* ------------------------- Source sets ------------------------- */

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs(layout.buildDirectory.dir("gdx-natives/lib"))
        }
    }
}

/* ------------------------- Versions ------------------------- */

val roomVersion = "2.6.1"
val gdxVersion = "1.12.1"
val media3Version = "1.4.1"

/* ------------------------- Dependencies ------------------------- */

dependencies {

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

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

    // Database & Security
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    implementation("com.google.crypto.tink:tink-android:1.12.0")

    // Media & Documents
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("com.tom-roush:pdfbox-android:2.0.26.0")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    // Utils & Network
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("io.getstream:stream-webrtc-android:1.2.0")
    implementation("io.getstream:stream-webrtc-android-compose:1.1.2")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.22")

    // libGDX
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64").forEach {
        runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$it")
    }

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.10")

    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")
}

/* ------------------------- GDX natives copy ------------------------- */

val copyAndroidNatives = tasks.register<Copy>("copyAndroidNatives") {
    val platforms = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

    platforms.forEach { platform ->
        val cfg = configurations.detachedConfiguration(
            dependencies.create("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$platform")
        )

        from(cfg.map { zipTree(it) }) {
            include("*.so")
            into("lib/$platform")
        }
    }

    into(layout.buildDirectory.dir("gdx-natives"))
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(copyAndroidNatives)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(copyAndroidNatives)
}

tasks.matching {
    it.name.startsWith("merge") && it.name.endsWith("JniLibFolders")
}.configureEach {
    dependsOn(copyAndroidNatives)
}
