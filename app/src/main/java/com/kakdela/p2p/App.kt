package com.kakdela.p2p

import com.kakdela.p2p.vpn.service.VpnService
import android.app.Application
import android.content.Intent
import com.google.firebase.FirebaseApp
import com.kakdela.p2p.vpn.service.VpnService

class App : Application() {

    // включить VPN при старте
    private val vpnAutoStart = true

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        if (vpnAutoStart) {
            startService(Intent(this, VpnService::class.java))
        }
    }
}
