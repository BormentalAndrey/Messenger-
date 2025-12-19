package com.kakdela.p2p.vpn.service

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import com.kakdela.p2p.vpn.core.VpnBackend
import com.kakdela.p2p.vpn.core.WgKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VpnService : Service() {
    private val vpnBackend by lazy { VpnBackend(this) }
    private val keyStore by lazy { WgKeyStore(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Мы НЕ вызываем super.onStartCommand(intent, flags, startId) так как это не VpnService, а обычный Service, 
        // управляющий WireGuard бэкендом.
        
        CoroutineScope(Dispatchers.IO).launch {
            val privateKey = keyStore.getPrivateKey()
            val config = vpnBackend.buildWarpConfig(privateKey)
            
            // WireGuard библиотека сама сделает VpnService.Builder внутри, 
            // но чтобы ограничить только нашим приложением:
            // В данной библиотеке (WgQuickBackend) для Split Tunneling нужно добавить 
            // параметр в конфиг интерфейса, но проще всего это работает так:
            vpnBackend.up(config)
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        vpnBackend.down()
        super.onDestroy()
    }
}

