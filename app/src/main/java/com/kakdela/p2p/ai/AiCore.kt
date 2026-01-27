package com.kakdela.p2p.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Collections

// --- Memory Store ---
object AiMemoryStore {
    // Используем синхронизированный список для потокобезопасности
    private val memory = Collections.synchronizedList(mutableListOf<String>())

    fun remember(text: String) {
        if (text.length > 20) {
            memory.add(text)
            // Ограничиваем память последними 50 записями
            if (memory.size > 50) {
                memory.removeAt(0)
            }
        }
    }

    fun context(): String {
        synchronized(memory) {
            return memory.joinToString("\n")
        }
    }
}

// --- RAG Engine ---
object RagEngine {
    private val docs = Collections.synchronizedList(mutableListOf<String>())

    fun addLocal(text: String) {
        addToDocs(text)
    }

    fun addWeb(text: String) {
        addToDocs(text)
    }

    private fun addToDocs(text: String) {
        if (text.isBlank()) return
        docs.add(text)
        if (docs.size > 100) docs.removeAt(0) // Увеличил лимит до 100
    }

    fun relevant(query: String): String {
        synchronized(docs) {
            // Простейший поиск по ключевым словам
            val tokens = query.split(" ").filter { it.length > 3 }
            return docs.filter { doc ->
                tokens.any { token -> doc.contains(token, ignoreCase = true) }
            }
            .takeLast(5)
            .joinToString("\n\n")
        }
    }
}

// --- Web Searcher ---
object WebSearcher {
    private val client = OkHttpClient()

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.duckduckgo.com/?q=${query.replace(" ", "+")}&format=json&no_redirect=1&skip_disambig=1"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:109.0) Gecko/109.0 Firefox/115.0") // Важно для прохождения защиты
                .build()

            val resp = client.newCall(request).execute()
            val body = resp.body?.string() ?: return@withContext ""
            
            if (body.isEmpty()) return@withContext ""

            val json = JSONObject(body)
            // Пытаемся достать Abstract, если нет - RelatedTopics
            var answer = json.optString("AbstractText")
            
            if (answer.isEmpty()) {
                val related = json.optJSONArray("RelatedTopics")
                if (related != null && related.length() > 0) {
                    answer = related.optJSONObject(0)?.optString("Text") ?: ""
                }
            }
            
            answer.ifBlank { "" }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
