package com.kakdela.p2p.vpn.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kakdela.p2p.vpn.model.VpnServer
import java.io.BufferedReader
import java.io.InputStreamReader

class ServerRepository(private val context: Context) {
    
    fun load(): List<VpnServer> {
        return try {
            val inputStream = context.assets.open("vpn/servers.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }
            
            val type = object : TypeToken<List<VpnServer>>() {}.type
            Gson().fromJson(jsonString, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

