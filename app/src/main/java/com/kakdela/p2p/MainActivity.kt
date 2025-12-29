package com.kakdela.p2p

import android.content.Intent
import android.net.VpnService as AndroidVpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import com.kakdela.p2p.ui.theme.Theme
import com.kakdela.p2p.vpn.service.KakdelaVpnService

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            startVpn()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        checkAndStartVpn()

        setContent {
            Theme {
                // Твой UI
            }
        }
    }

    private fun checkAndStartVpn() {
        val intent = AndroidVpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpn()
        }
    }

    private fun startVpn() {
        val intent = Intent(this, KakdelaVpnService::class.java)
            .setAction(KakdelaVpnService.ACTION_CONNECT)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

