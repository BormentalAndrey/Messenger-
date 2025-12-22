package com.kakdela.p2p.vpn.core

import android.content.Context
import com.wireguard.crypto.KeyPair
import com.wireguard.crypto.Key

class WgKeyStore(context: Context) {

    private val prefs = context.getSharedPreferences("wg_keys", Context.MODE_PRIVATE)

    fun getPrivateKey(): String {
        prefs.getString("priv", null)?.let { return it }

        val keyPair = KeyPair()
        val priv = keyPair.privateKey.toBase64()
        prefs.edit().putString("priv", priv).apply()
        return priv
    }

    fun getPublicKey(): String {
        val priv = getPrivateKey()
        return KeyPair(Key.fromBase64(priv)).publicKey.toBase64()
    }
}
