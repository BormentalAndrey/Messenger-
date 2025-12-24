package com.kakdela.p2p.vpn.core

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Date

class WarpRegistrar {

    // Структура ответа Cloudflare
    data class WarpResponse(
        val id: String?,
        val config: ConfigData?
    )

    data class ConfigData(
        val interfaceData: InterfaceData?,
        val peers: List<PeerData>?
    )

    data class InterfaceData(
        val addresses: AddressData?
    )

    data class AddressData(
        val v4: String?,
        val v6: String?
    )

    data class PeerData(
        val public_key: String?,
        val endpoint: EndpointData?
    )

    data class EndpointData(
        val v4: String?,
        val host: String?
    )

    data class RegBody(
        val key: String,
        val tos: String = Date().toInstant().toString(),
        val type: String = "Android",
        val locale: String = "en_US"
    )

    suspend fun register(publicKey: String): WarpResponse? = withContext(Dispatchers.IO) {
        try {
            Log.d("Warp", "Регистрация в Cloudflare...")
            val url = URL("https://api.cloudflareclient.com/v0a2484/reg")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("User-Agent", "okhttp/3.12.1")
            conn.doOutput = true

            val jsonBody = Gson().toJson(RegBody(key = publicKey))
            
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(jsonBody) }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                Log.d("Warp", "Успех! Конфиг получен.")
                return@withContext Gson().fromJson(responseText, WarpResponse::class.java)
            } else {
                Log.e("Warp", "Ошибка API: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            Log.e("Warp", "Сбой сети: ${e.message}")
        }
        return@withContext null
    }
}

