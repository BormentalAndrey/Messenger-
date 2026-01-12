package com.kakdela.p2p

import android.app.Application
import android.util.Log
import com.kakdela.p2p.api.WebViewApiClient
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.security.CryptoManager

class MyApplication : Application() {

    val identityRepository: IdentityRepository by lazy {
        IdentityRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        
        try {
            // 1. Инициализация WebView для обхода защиты.
            // Запускает скрытый браузер и начинает прогрев антибота InfinityFree.
            WebViewApiClient.init(this)
            
            // 2. Инициализация криптографии.
            CryptoManager.init(this)
            
            val myId = identityRepository.getMyId()
            if (myId.isNotEmpty()) {
                Log.i(TAG, "Init Success. ID: $myId")
            } else {
                Log.w(TAG, "ID Empty. Waiting for registration.")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical Init Error: ${e.message}")
        }
    }

    /**
     * Вызывается при эмуляции остановки или реальном убийстве процесса системой.
     * Полезно для очистки WebView.
     */
    override fun onTerminate() {
        WebViewApiClient.destroy()
        super.onTerminate()
    }

    companion object {
        private const val TAG = "MyApplication"
    }
}
