package com.kakdela.p2p

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import com.kakdela.p2p.ui.navigation.Routes
import com.kakdela.p2p.ui.theme.KakdelaTheme
import com.kakdela.p2p.ui.player.MusicManager

class MainActivity : ComponentActivity() {

    private lateinit var identityRepository: IdentityRepository
    private val TAG = "MainActivity"

    // Лаунчер для запроса разрешений: Музыка + Контакты
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: false
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        }
        
        val contactsGranted = permissions[Manifest.permission.READ_CONTACTS] ?: false

        if (audioGranted) {
            MusicManager.loadTracks(this)
        }
        
        if (!contactsGranted) {
            Log.w(TAG, "Доступ к контактам отклонен пользователем")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Инициализация систем безопасности и данных
        try {
            CryptoManager.init(applicationContext)
            identityRepository = IdentityRepository(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка инициализации: ${e.message}")
        }

        // 2. Запуск фонового P2P-сервиса
        startP2PService()

        // 3. Проверка разрешений при запуске
        checkAndRequestPermissions()

        // 4. Определение состояния авторизации
        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)

        setContent {
            KakdelaTheme {
                val navController = rememberNavController()
                val repo = remember { identityRepository }

                // ПРАВКА: Используем константы из Routes, чтобы избежать IllegalArgumentException.
                // Если isLoggedIn == true, идем сразу в CHATS.
                // Если false, идем в SPLASH, который покажет лого и перекинет на CHOICE.
                val startRoute = if (isLoggedIn) Routes.CHATS else Routes.SPLASH

                NavGraph(
                    navController = navController,
                    identityRepository = repo,
                    startDestination = startRoute
                )
            }
        }
    }

    private fun startP2PService() {
        try {
            val serviceIntent = Intent(this, P2PService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска сервиса: ${e.message}")
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Контакты
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_CONTACTS)
        }

        // Музыка/Хранилище
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                MusicManager.loadTracks(this)
            }
        } else {
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
