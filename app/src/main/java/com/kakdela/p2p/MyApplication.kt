package com.kakdela.p2p

import android.app.Application
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.security.CryptoManager

/**
 * MyApplication — точка инициализации всего приложения.
 *
 * Здесь:
 * 1) Инициализируется криптография (Tink + ключи)
 * 2) Создаётся единый IdentityRepository
 *
 * ВАЖНО:
 * - Этот класс должен быть указан в AndroidManifest.xml
 *   android:name=".MyApplication"
 */
class MyApplication : Application() {

    lateinit var identityRepository: IdentityRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // 1️⃣ Инициализация CryptoManager (ключи, Tink)
        CryptoManager.init(applicationContext)

        // 2️⃣ Создание единственного репозитория на всё приложение
        identityRepository = IdentityRepository(applicationContext)
    }
}
