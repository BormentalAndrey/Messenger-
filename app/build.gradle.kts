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

        getByName("debug").apply {
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

    kotlinOptions { jvmTarget = "17" }

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

    sourceSets.getByName("main") {
        jniLibs.srcDirs(layout.buildDirectory.dir("gdx-natives/lib"))
    }
}

// ---------------- Dependencies ----------------
val markwonVersion = "4.6.2"
val roomVersion = "2.6.1"
val gdxVersion = "1.12.1"
val media3Version = "1.4.1"

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Compose, Room, PDFBox, Tink, Media3, Koin, WebRTC, libGDX...
    // (Остальной код как у тебя — не меняем)
}

// ---------------- Copy GDX Natives ----------------
val copyAndroidNatives = tasks.register<Copy>("copyAndroidNatives") {
    val platforms = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    platforms.forEach { platform ->
        val jarConfiguration = configurations.detachedConfiguration(
            dependencies.create("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$platform")
        )
        from(jarConfiguration.map { zipTree(it) }) { include("*.so"); into("lib/$platform") }
    }
    into(layout.buildDirectory.dir("gdx-natives"))
}
tasks.withType<JavaCompile>().configureEach { dependsOn(copyAndroidNatives) }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { dependsOn(copyAndroidNatives) }

// ---------------- Termux Bootstrap ----------------
val packageVariant = System.getenv("TERMUX_PACKAGE_VARIANT") ?: "apt-android-7"

fun downloadBootstrap(arch: String, expectedChecksum: String, version: String) {
    val digest = MessageDigest.getInstance("SHA-256")
    val file = File(projectDir, "src/main/cpp/bootstrap-$arch.zip")
    if (file.exists()) {
        file.inputStream().use {
            val buffer = ByteArray(8192)
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        var checksum = BigInteger(1, digest.digest()).toString(16)
        while (checksum.length < 64) checksum = "0$checksum"
        if (checksum == expectedChecksum) return
        file.delete()
    }

    val remoteUrl =
        "https://github.com/termux/termux-packages/releases/download/bootstrap-$version/bootstrap-$arch.zip"
    println("Downloading $remoteUrl ...")
    val connection = URL(remoteUrl).openConnection() as HttpURLConnection
    connection.followRedirects = true
    digest.reset()
    connection.inputStream.use { input ->
        file.parentFile.mkdirs()
        BufferedOutputStream(FileOutputStream(file)).use { output ->
            DigestInputStream(input, digest).use { digestStream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = digestStream.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
    }
    val checksum = BigInteger(1, digest.digest()).toString(16).padStart(64, '0')
    if (checksum != expectedChecksum) {
        file.delete()
        throw GradleException("Wrong checksum for $remoteUrl: expected $expectedChecksum, actual $checksum")
    }
}

val downloadBootstraps = tasks.register("downloadBootstraps") {
    doLast {
        if (packageVariant == "apt-android-7") {
            val version = "2022.04.28-r5+$packageVariant"
            downloadBootstrap("aarch64", "4a51a7eb209fe82efc24d52e3cccc13165f27377290687cb82038cbd8e948430", version)
            downloadBootstrap("arm", "6459a786acbae50d4c8a36fa1c3de6a4dd2d482572f6d54f73274709bd627325", version)
            downloadBootstrap("i686", "919d212b2f19e08600938db4079e794e947365022dbfd50ac342c50fcedcd7be", version)
            downloadBootstrap("x86_64", "61b02fdc03ea4f5d9da8d8cf018013fdc6659e6da6cbf44e9b24d1c623580b89", version)
        } else if (packageVariant == "apt-android-5") {
            val version = "2022.04.28-r6+$packageVariant"
            downloadBootstrap("aarch64", "913609d439415c828c5640be1b0561467e539cb1c7080662decaaca2fb4820e7", version)
            downloadBootstrap("arm", "26bfb45304c946170db69108e5eb6e3641aad751406ce106c80df80cad2eccf8", version)
            downloadBootstrap("i686", "46dcfeb5eef67ba765498db9fe4c50dc4690805139aa0dd141a9d8ee0693cd27", version)
            downloadBootstrap("x86_64", "615b590679ee6cd885b7fd2ff9473c845e920f9b422f790bb158c63fe42b8481", version)
        } else {
            throw GradleException("Unsupported TERMUX_PACKAGE_VARIANT $packageVariant")
        }
    }
}

// ---------------- Hook into preBuild ----------------
afterEvaluate {
    android.applicationVariants.forEach { variant ->
        variant.preBuildProvider.configure {
            dependsOn(downloadBootstraps)
        }
    }
}

// ---------------- Clean ----------------
tasks.named("clean") {
    doLast {
        fileTree("src/main/cpp").matching { include("bootstrap-*.zip") }.forEach { it.delete() }
    }
}
