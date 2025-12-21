package com.kakdela.p2p

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.kakdela.p2p.ui.navigation.NavGraph
import com.kakdela.p2p.ui.theme.Theme
import com.kakdela.p2p.vpn.service.VpnService as AppVpnService // Алиас для нашего сервиса

class MainActivity : ComponentActivity() {

    // Лаунчер для получения разрешения на VPN от системы
    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 1. Пытаемся запустить VPN сразу при старте приложения
        checkAndStartVpn()

        setContent {
            Theme {
                val navController = rememberNavController()
                NavGraph(navController)
            }
        }
    }

    private fun checkAndStartVpn() {
        // VpnService.prepare возвращает Intent, если нужно спросить разрешение, 
        // или null, если разрешение уже есть.
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // Разрешения нет -> запускаем системное диалоговое окно
            vpnPermissionLauncher.launch(intent)
        } else {
            // Разрешение уже есть -> просто запускаем сервис
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, AppVpnService::class.java)
        intent.action = AppVpnService.ACTION_CONNECT // Убедись, что эта константа есть в VpnService
        startService(intent)
    }
}

