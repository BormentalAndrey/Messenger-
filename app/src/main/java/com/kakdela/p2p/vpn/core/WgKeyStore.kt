package com.kakdela.p2p.vpn.core

import android.content.Context
import com.wireguard.crypto.Key
import java.util.*

class WgKeyStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("wg_keys", Context.MODE_PRIVATE)

    fun getPrivateKey(): String {
        val saved = prefs.getString("priv", null)
        if (saved != null) return saved

        val generated = Base64.getEncoder().encodeToString(Key.generatePrivateKey().key)
        prefs.edit().putString("priv", generated).apply()
        return generated
    }
}
