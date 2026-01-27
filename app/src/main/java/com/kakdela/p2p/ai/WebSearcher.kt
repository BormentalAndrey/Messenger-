package com.kakdela.p2p.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object WebSearcher {
    private val client = OkHttpClient()

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        val url = "https://api.duckduckgo.com/?q=${query.replace(" ", "+")}&format=json&no_redirect=1&skip_disambig=1"
        val request = Request.Builder().url(url).build()
        val resp = client.newCall(request).execute()
        val body = resp.body?.string() ?: return@withContext "Нет данных из сети"
        val json = JSONObject(body)
        val answer = json.optString("AbstractText")
        if (answer.isNotBlank()) answer else "По запросу '$query' данных нет"
    }
}
