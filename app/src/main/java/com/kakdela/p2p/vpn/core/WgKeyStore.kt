package com.kakdela.p2p.vpn.core

import android.content.Context
import android.util.Base64
import com.wireguard.crypto.Key
import java.security.SecureRandom

class WgKeyStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("wg_keys", Context.MODE_PRIVATE)

    fun getPrivateKey(): String {
        val existing = prefs.getString("private_key", null)
        if (existing != null) return existing

        val rnd = SecureRandom()
        val keyBytes = ByteArray(Key.KEY_SIZE)
        rnd.nextBytes(keyBytes)
        val privateKey = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
        prefs.edit().putString("private_key", privateKey).apply()
        return privateKey
    }
}
