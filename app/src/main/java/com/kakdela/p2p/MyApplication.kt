package com.kakdela.p2p

import android.app.Application
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.security.CryptoManager

class MyApplication : Application() {
    lateinit var identityRepository: IdentityRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Генерируем ключи и загружаем их
        CryptoManager.generateKeysIfNeeded(this)
        identityRepository = IdentityRepository(this)
    }
}
