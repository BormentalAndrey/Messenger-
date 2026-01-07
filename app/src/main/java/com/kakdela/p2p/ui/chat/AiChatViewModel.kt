package com.kakdela.p2p.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.BuildConfig
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean
)

class AiChatViewModel : ViewModel() {

    private val _messages = mutableListOf(
        ChatMessage(
            text = "Привет! Я AI-помощник. Задай вопрос 🙂",
            isUser = false
        )
    )
    val messages: List<ChatMessage> get() = _messages

    private val client = OkHttpClient()

    fun sendMessage(text: String, onUpdate: () -> Unit) {
        _messages.add(ChatMessage(text = text, isUser = true))
        onUpdate()

        viewModelScope.launch {
            val reply = askGemini(text)
            _messages.add(ChatMessage(text = reply, isUser = false))
            onUpdate()
        }
    }

    private fun askGemini(prompt: String): String {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return "❌ Gemini API ключ не настроен"
        }

        val body = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", listOf(
                    JSONObject().put(
                        "text",
                        "Ты помощник, который объясняет юридическую информацию простыми словами. Ты не юрист."
                    )
                ))
            })
            put("contents", listOf(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", listOf(JSONObject().put("text", prompt)))
                }
            ))
        }

        val request = Request.Builder()
            .url(
                "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-pro-latest:generateContent?key=${BuildConfig.GEMINI_API_KEY}"
            )
            .post(
                body.toString()
                    .toRequestBody("application/json".toMediaType())
            )
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    "⚠️ Ошибка Gemini: ${response.code}"
                } else {
                    val json = JSONObject(response.body!!.string())
                    json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                }
            }
        } catch (e: Exception) {
            "⚠️ Ошибка сети: ${e.message}"
        }
    }
}
