package com.kakdela.p2p

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.kakdela.p2p.vpn.service.KakdelaVpnService

class MainActivity : ComponentActivity() {

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            startService(Intent(this, KakdelaVpnService::class.java))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = VpnService.prepare(this)
        if (intent != null) {
            launcher.launch(intent)
        } else {
            startService(Intent(this, KakdelaVpnService::class.java))
        }
    }
}
