package com.kakdela.p2p.vpn.service

import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.kakdela.p2p.vpn.core.VpnBackend
import com.kakdela.p2p.vpn.core.WgKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WarpVpnService : VpnService() {

    private var conn: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()

        CoroutineScope(Dispatchers.IO).launch {
            val backend = VpnBackend(this@WarpVpnService)
            val priv = WgKeyStore(this@WarpVpnService).getPrivateKey()
            val cfg = backend.buildWarpConfig(priv)

            backend.up(cfg)

            val builder = Builder()
                .addAddress("172.16.0.2", 32)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)
                .setSession("Warp")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            conn = builder.establish()
        }
    }

    override fun onDestroy() {
        conn?.close()
        super.onDestroy()
    }
}
