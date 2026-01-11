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
import com.kakdela.p2p.ui.player.MusicManager
import com.kakdela.p2p.ui.theme.KakdelaTheme

class MainActivity : ComponentActivity() {

    private lateinit var identityRepository: IdentityRepository
    private val TAG = "MainActivity"

    // Expanded permission list for Chat, Calls, and Media
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

        if (!contactsGranted) Log.w(TAG, "Contacts permission denied")
        if (!micGranted) Log.w(TAG, "Mic permission denied (Voice notes won't work)")
        if (!notificationGranted) Log.w(TAG, "Notifications disabled")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 1. Security Initialization
        initSecurity()

        // 2. Start Background P2P Service
        startP2PService()

        // 3. Check Permissions
        checkAndRequestPermissions()

        // 4. Auth Logic
        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)

        setContent {
            KakdelaTheme {
                val navController = rememberNavController()
                // Remember repo to avoid recreating it on recomposition
                val repo = remember { identityRepository }

                // Route logic
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
            // Ensure ID exists
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

        // Notifications for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            essentialPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Media permissions logic
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            essentialPermissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            essentialPermissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            essentialPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // Filter already granted permissions
        essentialPermissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        // Load music if permission already granted
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
}
