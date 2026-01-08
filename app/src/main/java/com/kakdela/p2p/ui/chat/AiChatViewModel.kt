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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AiChatViewModel : ViewModel() {

    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: SnapshotStateList<ChatMessage> get() = _messages

    var isTyping = mutableStateOf(false)
        private set

    private val client = OkHttpClient()

    // 🔑 Тестовый ключ Gemini для локальной сборки
    private val GEMINI_API_KEY = "AIzaSyAi68xQGYNj3-45Y-71bV29sXa8KLfAyLQ"

    companion object {
        const val GEMINI_MODEL = "gemini-2.5-pro"
        const val SAFETY_REJECT_CODE = 2
    }

    init {
        // Начальное сообщение от ИИ
        _messages.add(
            ChatMessage(
                text = "Привет! Я твой продвинутый ИИ-ассистент. Чем могу помочь?",
                isMine = false
            )
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = ChatMessage(text = text, isMine = true)
        _messages.add(userMsg)

        isTyping.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val reply = askGeminiWithHistory(text)
            withContext(Dispatchers.Main) {
                isTyping.value = false
                _messages.add(ChatMessage(text = reply, isMine = false))
            }
        }
    }

    private suspend fun askGeminiWithHistory(prompt: String): String {
        if (GEMINI_API_KEY.isBlank()) return "❌ Ошибка: API ключ не найден."

        return try {
            val historyJson = JSONArray()
            _messages.takeLast(12).forEach { msg ->
                val role = if (msg.isMine) "user" else "assistant"
                historyJson.put(JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                })
            }

            val requestBody = JSONObject().apply {
                put("messages", historyJson)
                put(
                    "instructions",
                    "Ты профессиональный ИИ-помощник в P2P мессенджере. Отвечай кратко, грамотно и с неоновым киберпанк-стилем."
                )
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1/models/$GEMINI_MODEL:generateMessage?key=$GEMINI_API_KEY")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseData = response.body?.string() ?: ""
                if (!response.isSuccessful) return "⚠️ Ошибка Gemini: ${response.code}"

                val json = JSONObject(responseData)
                val candidate = json.getJSONArray("candidates").getJSONObject(0)

                // Проверка кода отклонения контента
                val safetyCode = candidate.optInt("safetyRejectionCode", 0)
                if (safetyCode == SAFETY_REJECT_CODE) {
                    return "⚠️ Ответ отклонен системой безопасности"
                }

                candidate.getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            }
        } catch (e: Exception) {
            "⚠️ Ошибка соединения: ${e.localizedMessage}"
        }
    }
}
