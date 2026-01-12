package com.kakdela.p2p

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
    private val TAG = "MainActivity"

    // Невидимый WebView для Anti-Bot куки
    private var antiBotWebView: WebView? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: false
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        }

        if (audioGranted) MusicManager.loadTracks(this)

        if (permissions[Manifest.permission.READ_CONTACTS] == false) Log.w(TAG, "Contacts permission denied")
        if (permissions[Manifest.permission.RECORD_AUDIO] == false) Log.w(TAG, "Mic permission denied")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            permissions[Manifest.permission.POST_NOTIFICATIONS] == false) Log.w(TAG, "Notifications disabled")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 0. Инициализация CookieStore
        CookieStore.init(applicationContext)

        // 1. Security
        initSecurity()

        // 2. P2P Service
        startP2PService()

        // 3. Permissions
        checkAndRequestPermissions()

        // 4. Anti-Bot Observer
        setupNetworkObserver()

        // 5. Navigation
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
        val permissionsToRequest = mutableListOf<String>()
        val essentialPermissions = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            essentialPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            essentialPermissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            essentialPermissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            essentialPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        essentialPermissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        // Load music if already granted
        val hasMusicAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (hasMusicAccess) MusicManager.loadTracks(this)

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun setupNetworkObserver() {
        lifecycleScope.launch {
            NetworkEvents.onAuthRequired.collectLatest {
                Log.d(TAG, "Anti-Bot cookie refresh triggered")
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
                                Log.i(TAG, "Anti-Bot cookie updated successfully")
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
