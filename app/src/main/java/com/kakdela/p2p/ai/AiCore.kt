package com.kakdela.p2p.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.TimeUnit

// --- Memory Store ---
/**
 * Хранилище контекста диалога.
 * Позволяет ИИ "помнить" последние реплики.
 */
object AiMemoryStore {
    // Используем потокобезопасный список
    private val memory = Collections.synchronizedList(mutableListOf<String>())
    private const val MAX_MEMORY_SIZE = 50

    fun remember(text: String) {
        val cleanText = text.trim()
        if (cleanText.length > 20) {
            synchronized(memory) {
                memory.add(cleanText)
                if (memory.size > MAX_MEMORY_SIZE) {
                    memory.removeAt(0)
                }
            }
        }
    }

    fun context(): String {
        synchronized(memory) {
            return if (memory.isEmpty()) "" else memory.joinToString("\n")
        }
    }

    fun clear() {
        memory.clear()
    }
}

// --- RAG Engine ---
/**
 * Простая реализация RAG (Retrieval-Augmented Generation).
 * Ищет релевантную информацию в локальных документах и вебе по ключевым словам.
 */
object RagEngine {
    private val docs = Collections.synchronizedList(mutableListOf<String>())
    private const val MAX_DOCS_SIZE = 100

    fun addLocal(text: String) = addToDocs(text)

    fun addWeb(text: String) = addToDocs(text)

    private fun addToDocs(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        
        synchronized(docs) {
            // Предотвращаем дубликаты
            if (!docs.contains(cleanText)) {
                docs.add(cleanText)
                if (docs.size > MAX_DOCS_SIZE) docs.removeAt(0)
            }
        }
    }

    fun relevant(query: String): String {
        val tokens = query.lowercase().split(Regex("\\s+"))
            .filter { it.length > 3 }
            .take(10) // Ограничиваем количество токенов для скорости

        if (tokens.isEmpty()) return ""

        synchronized(docs) {
            return docs.filter { doc ->
                val lowerDoc = doc.lowercase()
                tokens.any { token -> lowerDoc.contains(token) }
            }
            .takeLast(5) // Берем самые свежие совпадения
            .joinToString("\n\n")
        }
    }
}

// --- Web Searcher ---
/**
 * Поисковый движок на базе DuckDuckGo Instant Answer API.
 * Работает без ключей API, идеально для P2P решений.
 */
object WebSearcher {
    // Настраиваем клиент с таймаутами, чтобы не ждать вечно при плохом интернете
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext ""

        // Правильное формирование URL с кодированием спецсимволов
        val urlBuilder = "https://api.duckduckgo.com/".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("format", "json")
            ?.addQueryParameter("no_redirect", "1")
            ?.addQueryParameter("skip_disambig", "1")
            ?.build() ?: return@withContext ""

        val request = Request.Builder()
            .url(urlBuilder)
            .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
            .build()

        try {
            // Используем .use для автоматического закрытия Response и Body (предотвращает утечки)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ""
                
                val bodyString = response.body?.string() ?: return@withContext ""
                if (bodyString.isBlank()) return@withContext ""

                val json = JSONObject(bodyString)
                
                // 1. Пробуем получить прямой ответ (Abstract)
                var result = json.optString("AbstractText")

                // 2. Если пусто, заглядываем в связанные темы
                if (result.isNullOrBlank()) {
                    val related = json.optJSONArray("RelatedTopics")
                    if (related != null && related.length() > 0) {
                        // Берем текст из первого связанного объекта
                        result = related.optJSONObject(0)?.optString("Text") ?: ""
                    }
                }

                return@withContext result.trim()
            }
        } catch (e: Exception) {
            // В продакшне лучше логировать через Log.e, но здесь оставляем для отладки
            e.printStackTrace()
            ""
        }
    }
}
