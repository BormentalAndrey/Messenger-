package com.kakdela.p2p

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.security.CryptoManager
import com.kakdela.p2p.services.P2PService
import com.kakdela.p2p.ui.navigation.NavGraph
import com.kakdela.p2p.ui.theme.KakdelaTheme

/**
 * Главная Activity приложения KakDela P2P Messenger
 */
class MainActivity : ComponentActivity() {

    private lateinit var identityRepository: IdentityRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1️⃣ Инициализация криптографии (ключи, Tink)
        CryptoManager.init(applicationContext)

        // 2️⃣ Инициализация репозитория идентичности / P2P
        identityRepository = IdentityRepository(applicationContext)

        // 3️⃣ Запуск P2P Service как Foreground Service (обязательно для Android 8+)
        val serviceIntent = Intent(this, P2PService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // 4️⃣ UI
        setContent {
            KakdelaTheme {
                val navController = rememberNavController()

                // repository сохраняется между рекомпозициями
                val repo = remember { identityRepository }

                NavGraph(
                    navController = navController,
                    identityRepository = repo
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        identityRepository.onDestroy()
    }
}
