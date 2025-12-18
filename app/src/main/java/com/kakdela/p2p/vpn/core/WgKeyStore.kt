package com.kakdela.p2p.vpn.core

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

class WgKeyStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("wg_keys", Context.MODE_PRIVATE)

    fun getPrivateKey(): String {
        val existing = prefs.getString("private_key", null)
        if (existing != null) return existing

        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        val key = Base64.encodeToString(random, Base64.NO_WRAP)

        prefs.edit().putString("private_key", key).apply()
        return key
    }
}
