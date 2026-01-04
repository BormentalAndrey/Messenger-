plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp")
}

// 1. Создаем отдельную конфигурацию для поиска нативных библиотек libGDX
val gdxNatives by configurations.creating

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
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    // 2. Указываем Android Plugin брать .so файлы из папки, куда мы их распакуем
    sourceSets {
        getByName("main") {
            jniLibs.srcDir(layout.buildDirectory.dir("intermediates/gdx-natives"))
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
        // ВАЖНО: Убедись, что версия Kotlin в проекте — 1.9.23. 
        // Если используешь Kotlin 2.0+, удали этот блок.
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/kotlinx-coroutines-core.kotlin_module"
        }
    }
}

// 3. Задача для распаковки .so файлов из jar-архивов gdx-platform
val unpackNatives = tasks.register<Copy>("unpackNatives") {
    description = "Unpacks libGDX native libraries from jars"
    
    // Берем файлы из созданной нами конфигурации
    from(gdxNatives.map { zipTree(it) })
    
    // Распаковываем в промежуточную папку билда (не в src!)
    into(layout.buildDirectory.dir("intermediates/gdx-natives"))
    
    include("**/libgdx.so")
    include("**/libgdx-box2d.so") // если используешь box2d
    
    // Перемещаем файлы из вложенных папок com/badlogic/gdx/natives-xxx/ в корень для jniLibs
    eachFile {
        val parts = path.split("/")
        if (parts.contains("natives-armeabi-v7a")) path = "armeabi-v7a/${name}"
        if (parts.contains("natives-arm64-v8a")) path = "arm64-v8a/${name}"
        if (parts.contains("natives-x86")) path = "x86/${name}"
        if (parts.contains("natives-x86_64")) path = "x86_64/${name}"
    }
    
    includeEmptyDirs = false
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// 4. Заставляем задачу выполняться перед компиляцией
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach {
    dependsOn(unpackNatives)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.1")

    implementation("com.wireguard.android:tunnel:1.0.20230706")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    // libGDX Core
    implementation("com.badlogicgames.gdx:gdx:1.12.1")
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.12.1")

    // libGDX Natives - добавляем и в обычные зависимости, и в нашу gdxNatives
    val gdxVersion = "1.12.1"
    val platforms = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    platforms.forEach { platform ->
        val dep = "com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$platform"
        gdxNatives(dep)
    }

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("io.getstream:stream-webrtc-android:1.2.0")
}

