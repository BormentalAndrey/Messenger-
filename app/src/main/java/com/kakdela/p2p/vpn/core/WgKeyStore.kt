package com.kakdela.p2p.vpn.core

import android.content.Context
import com.wireguard.crypto.KeyPair

class WgKeyStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("wg_keys", Context.MODE_PRIVATE)

    fun getPrivateKey(): String {
        val saved = prefs.getString("priv", null)
        if (saved != null) return saved

        val keyPair = KeyPair()  // Автоматически генерирует приватный и публичный ключ
        val generated = keyPair.privateKey.toBase64()
        prefs.edit().putString("priv", generated).apply()
        return generated
    }

    // Если нужен публичный ключ (для сервера)
    fun getPublicKey(): String {
        val privateKey = getPrivateKey()
        return KeyPair(com.wireguard.crypto.Key.fromBase64(privateKey)).publicKey.toBase64()
    }
}
