package com.kakdela.p2p.vpn.core

import android.content.Context
import android.util.Log

class WarpRegistrar(private val context: Context) {

    data class WarpConfig(
        val privateKey: String,
        val address: String = "172.16.0.2/32",
        val publicKey: String = "bmXOC+F1FxEMY9dyU9S47Vp00nU8NAs4W8uNP0R2D1s=",
        val endpoint: String = "162.159.193.2:2408"
    )

    fun load(onResult: (WarpConfig) -> Unit) {
        try {
            val priv = WgKeyStore(context).getPrivateKey()
            onResult(
                WarpConfig(privateKey = priv)
            )
        } catch (e: Exception) {
            Log.e("WarpRegistrar", "Error: ${e.message}")
        }
    }
}
