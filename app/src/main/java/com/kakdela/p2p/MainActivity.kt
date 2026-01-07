package com.kakdela.p2p

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.security.CryptoManager
import com.kakdela.p2p.ui.navigation.NavGraph
import com.kakdela.p2p.ui.theme.KakdelaTheme

class MainActivity : ComponentActivity() {

    // Стек P2P компонентов
    private lateinit var identityRepository: IdentityRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // 1. Сначала запускаем криптографию (генерация или загрузка ключей RSA/AES)
            CryptoManager.init(applicationContext)
            
            // 2. Инициализируем сетевой узел (Identity + DHT + Signaling)
            identityRepository = IdentityRepository(applicationContext)

            Log.i("P2P_START", "Node initialized: ${identityRepository.getMyPublicKeyHash()}")
        } catch (e: Exception) {
            Log.e("P2P_FATAL", "Failed to start P2P node", e)
        }

        setContent {
            KakdelaTheme {
                val navController = rememberNavController()
                
                // 3. Запускаем навигацию, прокидывая репозиторий как источник данных
                NavGraph(
                    navController = navController, 
                    identityRepository = identityRepository
                )
            }
        }
    }

    override fun onDestroy() {
        // Здесь можно добавить закрытие сокетов, если это необходимо
        super.onDestroy()
    }
}

