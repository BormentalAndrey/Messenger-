package com.kakdela.p2p.vpn.core

import android.content.Context
import android.util.Base64
import com.wireguard.crypto.KeyPair

class WgKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("vpn_keys", Context.MODE_PRIVATE)

    fun getPrivateKey(): String {
        var key = prefs.getString("private_key", null)
        if (key == null) {
            // Генерируем новую пару ключей через библиотеку WireGuard
            val keyPair = KeyPair()
            key = keyPair.privateKey.toBase64()
            val pub = keyPair.publicKey.toBase64()
            
            prefs.edit()
                .putString("private_key", key)
                .putString("public_key", pub)
                .apply()
        }
        return key!!
    }

    fun getPublicKey(): String {
        // Убеждаемся, что ключи созданы
        if (!prefs.contains("public_key")) {
            getPrivateKey()
        }
        return prefs.getString("public_key", "") ?: ""
    }
}

