import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    kotlin("kapt")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
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
        vectorDrawables.useSupportLibrary = true

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }

        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("GEMINI_API_KEY") ?: ""}\""
        )
    }

    signingConfigs {
        // Конфигурация release создается с нуля
        create("release") {
            storeFile = file("my-release-key.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS")
                ?: localProperties.getProperty("RELEASE_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD")
                ?: localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: ""
            enableV1Signing = true
            enableV2Signing = true
        }

        // ИСПРАВЛЕНО: Используем getByName, так как debug существует всегда
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
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST",
            "META-INF/NOTICE*",
            "META-INF/LICENSE*",
            "META-INF/kotlinx-coroutines-core.kotlin_module",
            "META-INF/tink/**"
        )
        jniLibs.pickFirsts.add("**/*.so")
        jniLibs.useLegacyPackaging = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs(layout.buildDirectory.dir("gdx-natives/lib"))
        }
    }
}

// ------------------------- Dependencies -------------------------
val roomVersion = "2.6.1"
val gdxVersion = "1.12.1"
val media3Version = "1.4.1"

dependencies {
    // Termux
    implementation("com.termux:termux-android:0.117")
    implementation("com.termux:termux-boot:0.117")
    implementation("com.termux:termux-view:0.117")

    // Core & Lifecycle
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

    // DB & Security
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    implementation("com.google.crypto.tink:tink-android:1.12.0")

    // Media & Docs
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
    listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64").forEach { platform ->
        runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$platform")
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.10")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")
}

// ------------------------- Copy GDX Natives -------------------------
val copyAndroidNatives = tasks.register<Copy>("copyAndroidNatives") {
    val platforms = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    platforms.forEach { platform ->
        val jarConfiguration = configurations.detachedConfiguration(
            dependencies.create("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-$platform")
        )
        from(jarConfiguration.map { zipTree(it) }) { include("*.so"); into("lib/$platform") }
    }
    into(layout.buildDirectory.dir("gdx-natives"))
}

tasks.withType<JavaCompile>().configureEach { dependsOn(copyAndroidNatives) }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { dependsOn(copyAndroidNatives) }

// ------------------------- Termux Bootstrap -------------------------
val packageVariant = System.getenv("TERMUX_PACKAGE_VARIANT") ?: "apt-android-7"

fun downloadBootstrap(arch: String, expectedChecksum: String, version: String) {
    val digest = MessageDigest.getInstance("SHA-256")
    val file = File(projectDir, "src/main/cpp/bootstrap-$arch.zip")

    if (file.exists()) {
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                digest.update(buffer, 0, read)
            }
        }
        val checksum = BigInteger(1, digest.digest()).toString(16).padStart(64, '0')
        if (checksum == expectedChecksum) return
        file.delete()
    }

    val remoteUrl = "https://github.com/termux/termux-packages/releases/download/bootstrap-$version/bootstrap-$arch.zip"
    println("Downloading $remoteUrl ...")
    val connection = URL(remoteUrl).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    
    digest.reset()
    file.parentFile.mkdirs()
    
    connection.inputStream.use { input ->
        BufferedOutputStream(FileOutputStream(file)).use { output ->
            DigestInputStream(input, digest).use { dis ->
                dis.copyTo(output)
            }
        }
    }

    val finalChecksum = BigInteger(1, digest.digest()).toString(16).padStart(64, '0')
    if (finalChecksum != expectedChecksum) {
        file.delete()
        throw GradleException("Checksum mismatch for $arch")
    }
}

val downloadBootstraps = tasks.register("downloadBootstraps") {
    doLast {
        if (packageVariant == "apt-android-7") {
            val version = "2022.04.28-r5+apt-android-7"
            downloadBootstrap("aarch64", "4a51a7eb209fe82efc24d52e3cccc13165f27377290687cb82038cbd8e948430", version)
            downloadBootstrap("arm", "6459a786acbae50d4c8a36fa1c3de6a4dd2d482572f6d54f73274709bd627325", version)
            downloadBootstrap("i686", "919d212b2f19e08600938db4079e794e947365022dbfd50ac342c50fcedcd7be", version)
            downloadBootstrap("x86_64", "61b02fdc03ea4f5d9da8d8cf018013fdc6659e6da6cbf44e9b24d1c623580b89", version)
        }
    }
}

// ------------------------- Hook into preBuild -------------------------
afterEvaluate {
    android.applicationVariants.forEach { variant ->
        variant.preBuildProvider.configure {
            dependsOn(downloadBootstraps)
        }
    }
}

// ------------------------- Clean -------------------------
tasks.named("clean") {
    doLast {
        fileTree("src/main/cpp").matching { include("bootstrap-*.zip") }.forEach { it.delete() }
    }
}
