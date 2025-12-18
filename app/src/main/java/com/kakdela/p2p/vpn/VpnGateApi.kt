package com.kakdela.p2p.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object VpnGateApi {
    suspend fun fetchServers(): List<VpnServer> = withContext(Dispatchers.IO) {
        val csv = URL("https://www.vpngate.net/api/iphone/").readText()
        val lines = csv.split("\n").drop(2)

        lines.mapNotNull { row ->
            val cols = row.split(",")
            if (cols.size > 15) {
                VpnServer(
                    host = cols[1],
                    ip = cols[2],
                    ping = cols[4].toIntOrNull() ?: 9999,
                    ovpnBase64 = cols[14]
                )
            } else null
        }
    }
}

data class VpnServer(
    val host: String,
    val ip: String,
    val ping: Int,
    val ovpnBase64: String
)
