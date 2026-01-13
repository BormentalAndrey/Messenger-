package com.kakdela.p2p

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.webkit.CookieManager
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

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            handlePermissionsResult(it)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 0. Cookie storage (singleton)
        CookieStore.init(applicationContext)

        // 1. Identity (Crypto уже инициализирован в MyApplication)
        identityRepository = IdentityRepository(applicationContext)
        identityRepository.getMyId()

        // 2. Network debug
        initNetworkDebug()

        // 3. P2P service
        startP2PService()

        // 4. Permissions
        checkAndRequestPermissions()

        // 5. Network observer (anti-bot trigger)
        setupNetworkObserver()

        // 6. Принудительный прогрев антибота при старте
        startSilentCookieRefresh()

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
                MyServerApiFactory.instance
                Log.i(TAG, "Network Sniffer initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Sniffer Init Error: ${e.message}")
            }
        }
    }

    private fun startP2PService() {
        try {
            val intent = Intent(this, P2PService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Service Start Error: ${e.message}")
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }

        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
            permissions += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
            permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun handlePermissionsResult(permissions: Map<String, Boolean>) {
        if (permissions[Manifest.permission.READ_MEDIA_AUDIO] == true ||
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        ) {
            MusicManager.loadTracks(this)
        }

        permissions.forEach { (perm, granted) ->
            if (!granted) Log.w(TAG, "Permission denied: $perm")
        }
    }

    private fun setupNetworkObserver() {
        lifecycleScope.launch {
            NetworkEvents.onAuthRequired.collectLatest {
                Log.d(TAG, "Anti-bot refresh requested")
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

                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(this@apply, true)
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            url?.let {
                                CookieStore.updateFromWebView(applicationContext, it)
                                Log.i(TAG, "Anti-bot cookies refreshed")
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
