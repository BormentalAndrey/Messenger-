package com.kakdela.p2p

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.kakdela.p2p.api.MyServerApiFactory
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.network.CookieStore
import com.kakdela.p2p.network.NetworkEvents
import com.kakdela.p2p.security.CryptoManager
import com.kakdela.p2p.services.P2PService
import com.kakdela.p2p.ui.navigation.NavGraph
import com.kakdela.p2p.ui.navigation.Routes
import com.kakdela.p2p.ui.player.MusicManager
import com.kakdela.p2p.ui.theme.KakdelaTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var identityRepository: IdentityRepository
    private val TAG = "P2P_DEBUG_MAIN"

    private var antiBotWebView: WebView? = null

    // Лаунчер для обычных разрешений
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionsResult(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 0. Инициализация хранилища кук
        CookieStore.init(applicationContext)

        // 1. Безопасность и Репозиторий
        initSecurity()

        // 2. СНИФФЕР: Принудительный запуск API Фабрики для создания файла p2p_log.txt
        initNetworkDebug()

        // 3. Запуск фонового P2P сервиса
        startP2PService()

        // 4. Проверка разрешений (включая спец. доступ к файлам для логов)
        checkAndRequestPermissions()

        // 5. Наблюдатель за антиботом
        setupNetworkObserver()

        // 6. Навигация
        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)

        setContent {
            KakdelaTheme {
                val navController = rememberNavController()
                val repo = remember { identityRepository }
                val startRoute = if (isLoggedIn) Routes.CHATS else Routes.SPLASH

                NavGraph(
                    navController = navController,
                    identityRepository = repo,
                    startDestination = startRoute
                )
            }
        }
    }

    private fun initNetworkDebug() {
        lifecycleScope.launch {
            try {
                // Обращение к инстансу спровоцирует выполнение блока init в Factory
                // и запись первой строки в p2p_log.txt
                val api = MyServerApiFactory.instance
                Log.i(TAG, "Network Sniffer (Internal) initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Sniffer Init Error: ${e.message}")
            }
        }
    }

    private fun initSecurity() {
        try {
            CryptoManager.init(applicationContext)
            identityRepository = IdentityRepository(applicationContext)
            identityRepository.getMyId()
        } catch (e: Exception) {
            Log.e(TAG, "Critical Init Error: ${e.message}")
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
            Log.e(TAG, "Service Start Error: ${e.message}")
        }
    }

    private fun checkAndRequestPermissions() {
        // 1. Проверка доступа ко ВСЕМ файлам для Android 11+ (нужно для записи логов в Documents)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }

        // 2. Стандартные разрешения
        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun handlePermissionsResult(permissions: Map<String, Boolean>) {
        if (permissions[Manifest.permission.READ_MEDIA_AUDIO] == true || 
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true) {
            MusicManager.loadTracks(this)
        }
        
        permissions.forEach { (perm, granted) ->
            if (!granted) Log.w(TAG, "Permission denied: $perm")
        }
    }

    private fun setupNetworkObserver() {
        lifecycleScope.launch {
            NetworkEvents.onAuthRequired.collectLatest {
                Log.d(TAG, "Anti-Bot refresh needed")
                startSilentCookieRefresh()
            }
        }
    }

    private fun startSilentCookieRefresh() {
        runOnUiThread {
            if (antiBotWebView == null) {
                antiBotWebView = WebView(this).apply {
                    visibility = View.GONE
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            url?.let {
                                CookieStore.updateFromWebView(applicationContext, it)
                                Log.i(TAG, "Anti-Bot cookie successfully stored")
                            }
                        }
                    }
                }
            }
            antiBotWebView?.loadUrl("http://kakdela.infinityfree.me/")
        }
    }

    override fun onDestroy() {
        antiBotWebView?.destroy()
        antiBotWebView = null
        super.onDestroy()
    }
}
