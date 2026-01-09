package com.kakdela.p2p

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.security.CryptoManager
import com.kakdela.p2p.services.P2PService
import com.kakdela.p2p.ui.navigation.NavGraph
import com.kakdela.p2p.ui.theme.KakdelaTheme
import com.kakdela.p2p.ui.player.MusicManager

class MainActivity : ComponentActivity() {

    private lateinit var identityRepository: IdentityRepository

    // Лаунчер для запроса разрешений (нужен для работы плеера и камеры)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: false
        val storageGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        
        if (audioGranted || storageGranted) {
            // Если разрешение получено, загружаем музыку
            MusicManager.loadTracks(this)
        } else {
            Toast.makeText(this, "Доступ к музыке ограничен", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Инициализируем криптографию
        CryptoManager.init(applicationContext)

        // 2. Инициализируем репозиторий
        identityRepository = IdentityRepository(applicationContext)

        // 3. Запуск P2P сервиса
        try {
            val serviceIntent = Intent(this, P2PService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Проверка разрешений для музыкального плеера
        checkAndRequestPermissions()

        setContent {
            KakdelaTheme {
                val navController = rememberNavController()
                val repo = remember { identityRepository }

                NavGraph(
                    navController = navController,
                    identityRepository = repo
                )
            }
        }
    }

    /**
     * Запрашивает необходимые разрешения в зависимости от версии Android
     */
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                // Разрешение уже есть, загружаем треки
                MusicManager.loadTracks(this)
            }
        } else {
            // Android 12 и ниже
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                MusicManager.loadTracks(this)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
