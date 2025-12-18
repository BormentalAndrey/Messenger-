// Файл: app/src/main/java/com/kakdela/p2p/App.kt
package com.kakdela.p2p

import android.app.Application
import android.content.Intent
import com.google.firebase.FirebaseApp
import com.kakdela.p2p.vpn.service.VpnService

class App : Application() {

    // Автозапуск VPN при старте приложения
    private val vpnAutoStart = true

    override fun onCreate() {
        super.onCreate()

        // Инициализация Firebase
        FirebaseApp.initializeApp(this)

        // Запуск VPN сервиса
        if (vpnAutoStart) {
            val intent = Intent(this, VpnService::class.java)
            try {
                startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

