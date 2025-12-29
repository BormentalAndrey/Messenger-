package com.kakdela.p2p

import android.app.Application
import android.content.Intent
import android.os.Build
import com.google.firebase.FirebaseApp
import com.kakdela.p2p.vpn.service.KakdelaVpnService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        
        val intent = Intent(this, KakdelaVpnService::class.java)
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

