package com.kakdela.p2p.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AiChatViewModel : ViewModel() {

    // ==============================
    // 🔑 ВСТАВЬ СВОЙ API-КЛЮЧ СЮДА
    // ==============================
    private val GEMINI_API_KEY = "AIzaSyBjrYYkT6jcR3j8jaXhHGooRvKVlTjRoKI"

    // ==============================
    // 📦 Состояние чата
    // ==============================
    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: SnapshotStateList<ChatMessage> get() = _messages

    val isTyping = mutableStateOf(false)

    private val client = OkHttpClient()

    // ==============================
    // ✅ ТОЛЬКО РАБОЧИЕ МОДЕЛИ
    // ==============================
    private val models = listOf(
        "gemini-2.5-flash",
        "gemini-3-flash-preview",
        "gemini-2.5-flash-lite"
    )

    init {
        _messages.add(
            ChatMessage(
                text = "ИИ подключён. Использую стабильные модели Gemini.",
                isMine = false
            )
        )
    }

    // ==============================
    // 📤 Отправка сообщения
    // ==============================
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        _messages.add(ChatMessage(text = text, isMine = true))
        isTyping.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val answer = requestWithFallback()

            withContext(Dispatchers.Main) {
                isTyping.value = false
                _messages.add(ChatMessage(text = answer, isMine = false))
            }
        }
    }

    // ==============================
    // 🔁 Перебор моделей
    // ==============================
    private fun requestWithFallback(): String {
        if (GEMINI_API_KEY.isBlank()) {
            return "❌ API ключ не задан"
        }

        var lastError = ""

        for (model in models) {
            try {
                return callGemini(model)
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"

                // лимит → пробуем следующую модель
                if (
                    lastError.contains("429") ||
                    lastError.contains("quota", true) ||
                    lastError.contains("RESOURCE_EXHAUSTED", true)
                ) {
                    continue
                }

                break
            }
        }

        return "🛑 Все модели временно недоступны\n$lastError"
    }

    // ==============================
    // 🌐 Реальный вызов Gemini API
    // ==============================
    private fun callGemini(model: String): String {

        val contents = JSONArray()

        // Последние 8 сообщений — безопасно по лимитам
        _messages.takeLast(8).forEach { msg ->
            contents.put(
                JSONObject().apply {
                    put("role", if (msg.isMine) "user" else "model")
                    put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", msg.text)
                        )
                    )
                }
            )
        }

        val bodyJson = JSONObject().apply {
            put("contents", contents)

            // ВАЖНО: parts — это МАССИВ
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put(
                            "text",
                            "Ты полезный ИИ-ассистент. Отвечай кратко, по делу и понятно."
                        )
                    )
                )
            )
        }

        val request = Request.Builder()
            .url(
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$GEMINI_API_KEY"
            )
            .post(
                bodyJson
                    .toString()
                    .toRequestBody("application/json".toMediaType())
            )
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: $body")
            }

            val json = JSONObject(body)

            return json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }
}
