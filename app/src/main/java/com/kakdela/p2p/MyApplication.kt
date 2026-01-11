package com.kakdela.p2p

import android.app.Application
import android.util.Log
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.network.CookieStore
import com.kakdela.p2p.security.CryptoManager

/**
 * Основной класс приложения.
 * Отвечает за предварительную загрузку ключей, инициализацию сетевых кук 
 * и подготовку репозитория.
 */
class MyApplication : Application() {

    // Ленивая инициализация репозитория. 
    // Использует applicationContext для предотвращения утечек памяти.
    val identityRepository: IdentityRepository by lazy {
        IdentityRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        
        try {
            // 1. Инициализация хранилища кук.
            // Загружает сохраненную куку __test (InfinityFree) из SharedPreferences.
            // Без этого Retrofit будет отправлять пустые запросы при первом запуске.
            CookieStore.init(this)
            
            // 2. Инициализация криптографии.
            // Генерация или извлечение ключей из Android KeyStore.
            CryptoManager.init(this)
            
            // 3. Диагностика.
            // Проверяем наличие идентификатора и готовность системы.
            val myId = identityRepository.getMyId()
            
            if (myId.isNotEmpty()) {
                Log.i(TAG, "Инициализация успешна. Local Node ID: $myId")
            } else {
                Log.w(TAG, "Внимание: Node ID пуст. Возможно, ключи еще не созданы.")
            }
            
        } catch (e: Exception) {
            // В продакшне здесь можно добавить отправку лога в Firebase Crashlytics
            Log.e(TAG, "Критическая ошибка инициализации: ${e.stackTraceToString()}")
        }
    }

    companion object {
        private const val TAG = "MyApplication"
    }
}
