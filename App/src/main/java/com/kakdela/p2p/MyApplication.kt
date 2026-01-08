package com.kakdela.p2p

import android.app.Application
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.security.CryptoManager

class MyApplication : Application() {
    
    // Единый экземпляр для всего приложения
    lateinit var identityRepository: IdentityRepository
        private set

    override fun onCreate() {
        super.onCreate()
        
        // 1. Инициализация криптографии
        CryptoManager.init(this)
        
        // 2. Создание репозитория (один раз!)
        identityRepository = IdentityRepository(this)
    }
}

