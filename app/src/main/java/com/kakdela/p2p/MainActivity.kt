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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.network.CookieStore
import com.kakdela.p2p.services.P2PService
import com.kakdela.p2p.ui.navigation.NavGraph
import com.kakdela.p2p.ui.navigation.Routes
import com.kakdela.p2p.ui.player.MusicManager
import com.kakdela.p2p.ui.theme.KakdelaTheme

class MainActivity : ComponentActivity() {

    private lateinit var identityRepository: IdentityRepository
    private val TAG = "P2P_MAIN"

    private val permPrefs by lazy {
        getSharedPreferences("perm_prefs", Context.MODE_PRIVATE)
    }

    private var serviceStarted = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            handlePermissionsResult(it)
        }

    private val manageAllFilesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // После возврата из настроек продолжаем обычный флоу разрешений
            requestRuntimePermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 1. Cookie store
        CookieStore.init(applicationContext)

        // 2. IdentityRepository — ТОЛЬКО из Application
        identityRepository = (application as MyApplication).identityRepository

        // 3. Старт сети (один раз)
        identityRepository.startNetwork()

        // 4. Разрешения
        checkAndRequestPermissions()

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

    private fun startP2PService() {
        if (serviceStarted) return

        try {
            val intent = Intent(this, P2PService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            serviceStarted = true

        } catch (e: Exception) {
            Log.e(TAG, "Service Start Error", e)
        }
    }

    private fun checkAndRequestPermissions() {

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager() &&
            !permPrefs.getBoolean("asked_all_files", false)
        ) {

            permPrefs.edit()
                .putBoolean("asked_all_files", true)
                .apply()

            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )

                manageAllFilesLauncher.launch(intent)
                return

            } catch (e: Exception) {
                Log.w(TAG, "Cannot open all files permission screen", e)
            }
        }

        requestRuntimePermissions()
    }

    private fun requestRuntimePermissions() {

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

        if (
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true ||
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        ) {
            MusicManager.loadTracks(this)
        }

        // Сервис запускаем после флоу разрешений
        startP2PService()
    }
}
