package com.kakdela.p2p.ui.chat

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import android.net.Uri

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean
)

class AiChatViewModel : ViewModel() {

    // Реактивный список сообщений
    private val _messages = mutableStateListOf(
        ChatMessage(
            text = "Привет! Я AI-помощник. Задай вопрос 🙂",
            isUser = false
        )
    )
    val messages: List<ChatMessage> = _messages

    private val client = OkHttpClient()

    /**
     * Отправка текстового сообщения
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        _messages.add(ChatMessage(text = text, isUser = true))

        viewModelScope.launch {
            val reply = askGemini(text)
            _messages.add(ChatMessage(text = reply, isUser = false))
        }
    }

    /**
     * Отправка файла (например PDF, изображения и др.)
     */
    fun sendFile(uri: Uri) {
        _messages.add(ChatMessage(text = "📎 Файл отправлен: ${uri.lastPathSegment ?: "файл"}", isUser = true))

        // Здесь можно добавить логику загрузки файла на сервер или в облако
        // Пока просто добавляем сообщение
        viewModelScope.launch {
            // Ответ AI на отправку файла (пример)
            _messages.add(ChatMessage(text = "Файл успешно обработан AI.", isUser = false))
        }
    }

    /**
     * Запрос к Gemini API
     */
    private suspend fun askGemini(prompt: String): String = withContext(Dispatchers.IO) {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return@withContext "❌ Gemini API ключ не настроен"
        }

        val bodyJson = JSONObject().apply {
            put(
                "contents", listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", listOf(JSONObject().put("text", prompt)))
                    }
                )
            )
        }

        val request = Request.Builder()
            .url(
                "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-pro-latest:generateContent?key=${BuildConfig.GEMINI_API_KEY}"
            )
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    "⚠️ Gemini ошибка: ${response.code}"
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
