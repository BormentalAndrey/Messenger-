package com.kakdela.p2p

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    // Расширенный список разрешений для функционала чата, звонков и медиа
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: false
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        }
        
        val contactsGranted = permissions[Manifest.permission.READ_CONTACTS] ?: false
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else true

        if (audioGranted) {
            MusicManager.loadTracks(this)
        }
        
        if (!contactsGranted) Log.w(TAG, "Доступ к контактам отклонен")
        if (!micGranted) Log.w(TAG, "Доступ к микрофону отклонен (голосовые сообщения не будут работать)")
        if (!notificationGranted) Log.w(TAG, "Уведомления отключены")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Включение отображения "от края до края" для корректной работы инсетов клавиатуры
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 1. Инициализация безопасности
        initSecurity()

        // 2. Запуск фонового P2P-сервиса (необходим для входящих сообщений/звонков)
        startP2PService()

        // 3. Проверка и запрос критических разрешений
        checkAndRequestPermissions()

        // 4. Логика авторизации
        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)

        setContent {
            KakdelaTheme {
                val navController = rememberNavController()
                val repo = remember { identityRepository }

                // Маршрут: если залогинен — сразу в чаты, иначе — сплэш/авторизация
                val startRoute = if (isLoggedIn) Routes.CHATS else Routes.SPLASH

                NavGraph(
                    navController = navController,
                    identityRepository = repo,
                    startDestination = startRoute
                )
            }
        }
    }

    private fun initSecurity() {
        try {
            CryptoManager.init(applicationContext)
            identityRepository = IdentityRepository(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка инициализации: ${e.message}")
            // В реальном приложении здесь стоит показать диалог фатальной ошибки
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

        // Список необходимых разрешений
        val essentialPermissions = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        // Уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            essentialPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Медиа/Аудио в зависимости от версии
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            essentialPermissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            essentialPermissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            essentialPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // Фильтруем только те, что еще не даны
        essentialPermissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        // Если есть доступ к музыке, загружаем треки сразу
        val hasMusicAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (hasMusicAccess) {
            MusicManager.loadTracks(this)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        // При возврате в приложение можно обновить статус P2P узла, если нужно
    }

    override fun onDestroy() {
        super.onDestroy()
        // Мы не останавливаем P2PService здесь, так как он должен работать в фоне
    }
}
