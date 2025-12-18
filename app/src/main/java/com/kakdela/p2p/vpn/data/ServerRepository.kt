package com.kakdela.p2p.vpn.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kakdela.p2p.vpn.model.VpnServer

class ServerRepository(private val context: Context) {

    fun load(): List<VpnServer> {
        val json = context.assets.open("vpn/servers.json")
            .bufferedReader().use { it.readText() }

        val type = object : TypeToken<List<VpnServer>>() {}.type
        return Gson().fromJson(json, type)
    }
}
