package com.kakdela.p2p

import android.app.Application
import android.content.Intent
import android.os.Build
import com.google.firebase.FirebaseApp
import com.kakdela.p2p.vpn.service.VpnService

class App : Application() {

    private val vpnAutoStart = true

    override fun onCreate() {
        super.onCreate()

        // Инициализация Firebase
        FirebaseApp.initializeApp(this)

        // Запуск VPN сервиса
        if (vpnAutoStart) {
            val intent = Intent(this, VpnService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

