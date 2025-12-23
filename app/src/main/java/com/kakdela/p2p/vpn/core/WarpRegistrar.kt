package com.kakdela.p2p.vpn.core

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Date

class WarpRegistrar(private val context: Context) {

    data class WarpAccount(
        val id: String,
        val config: WarpConfigData
    )

    data class WarpConfigData(
        val interfaceData: InterfaceData,
        val peers: List<PeerData>
    )

    data class InterfaceData(
        val addresses: AddressData
    )

    data class AddressData(
        val v4: String,
        val v6: String
    )

    data class PeerData(
        @SerializedName("public_key") val publicKey: String,
        val endpoint: EndpointData
    )

    data class EndpointData(
        val v4: String,
        val host: String
    )

    // Модель запроса
    data class RegRequest(
        val key: String,
        val install_id: String = "",
        val fcm_token: String = "",
        val tos: String = Date().toInstant().toString(),
        val type: String = "Android",
        val locale: String = "en_US"
    )

    suspend fun register(publicKey: String): WarpAccount? = withContext(Dispatchers.IO) {
        try {
            Log.d("WarpRegistrar", "Регистрация ключа в Cloudflare...")
            val url = URL("https://api.cloudflareclient.com/v0a2484/reg")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("User-Agent", "okhttp/3.12.1")
            conn.doOutput = true

            val jsonBody = Gson().toJson(RegRequest(key = publicKey))
            
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { 
                it.write(jsonBody) 
            }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                Log.d("WarpRegistrar", "Успешная регистрация!")
                // Парсим ответ (структура сложная, упрощаем для примера, используя Gson)
                // В реальном ответе Cloudflare JSON немного сложнее, но основные поля совпадают.
                // Примечание: Cloudflare может вернуть структуру { "result": { ... } } или сразу объект.
                // Для надежности лучше использовать Retrofit, но здесь Pure Java для простоты.
                return@withContext Gson().fromJson(response, WarpAccount::class.java)
            } else {
                Log.e("WarpRegistrar", "Ошибка регистрации: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            Log.e("WarpRegistrar", "Exception: ${e.message}")
        }
        return@withContext null
    }
}

