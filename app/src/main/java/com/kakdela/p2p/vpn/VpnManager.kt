package com.kakdela.p2p.vpn

import android.content.Context
import android.util.Base64
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.DeviceStateReceiver
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringReader

object VpnManager {

    suspend fun connect(context: Context, server: VpnServer) = withContext(Dispatchers.IO) {
        val ovpn = String(Base64.decode(server.ovpnBase64, Base64.DEFAULT))

        val cp = ConfigParser()
        cp.parseConfig(StringReader(ovpn))
        val profile = cp.convertProfile()
        profile.mName = server.host

        val pm = ProfileManager.getInstance(context)
        pm.addProfile(profile)
        pm.saveProfile(context, profile)

        val intent = profile.prepareVPNService(context)
        if (intent != null) {
            // Need VPN permission
            context.startActivity(intent)
        } else {
            profile.connect(context)
        }
    }
}
