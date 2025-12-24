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
import com.kakdela.p2p.vpn.service.VpnService as MyVpnService

class MainActivity : ComponentActivity() {

    // Лаунчер для обработки диалога разрешения VPN
    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Пользователь нажал ОК (или Отмена), пробуем запустить сервис
        startService(Intent(this, MyVpnService::class.java).setAction(MyVpnService.ACTION_CONNECT))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // --- ЗАПУСК VPN ---
        checkAndStartVpn()

        setContent {
            Theme {
                val navController = rememberNavController()
                NavGraph(navController)
            }
        }
    }

    private fun checkAndStartVpn() {
        // VpnService.prepare возвращает Intent, если нужно спросить разрешение у пользователя,
        // или null, если разрешение уже есть.
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // Показываем системное диалоговое окно
            vpnPermissionLauncher.launch(intent)
        } else {
            // Разрешение уже есть, запускаем
            startService(Intent(this, MyVpnService::class.java).setAction(MyVpnService.ACTION_CONNECT))
        }
    }
}

