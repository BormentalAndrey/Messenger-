package com.kakdela.p2p

import android.app.Application
import android.util.Log
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.security.CryptoManager

/**
 * Основной класс приложения.
 * Здесь происходит базовая инициализация криптографии и сетевого репозитория.
 */
class MyApplication : Application() {

    // Репозиторий инициализируется лениво при первом обращении, 
    // чтобы гарантировать, что Context уже доступен.
    val identityRepository: IdentityRepository by lazy {
        IdentityRepository(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        
        try {
            // 1. Инициализируем CryptoManager (загружаем или генерируем RSA/ECC ключи)
            // Это критически важно сделать до любого обращения к identityRepository
            CryptoManager.init(this)
            
            // 2. Логируем успешный запуск (полезно для отладки в Logcat)
            val myId = identityRepository.getMyId()
            Log.i("MyApplication", "Приложение запущено. ID узла: $myId")
            
        } catch (e: Exception) {
            Log.e("MyApplication", "Ошибка при инициализации компонентов: ${e.message}")
        }
    }
}
