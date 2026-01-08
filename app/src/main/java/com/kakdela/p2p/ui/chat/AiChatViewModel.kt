package com.kakdela.p2p.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.BuildConfig
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

        // Добавляем сообщение пользователя
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
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) return "❌ Ошибка: API ключ не найден в конфигурации."

        return try {
            val historyJson = JSONArray()
            
            // Формируем историю для Gemini (последние 12 сообщений для экономии токенов)
            _messages.takeLast(12).forEach { msg ->
                val role = if (msg.isMine) "user" else "model"
                historyJson.put(JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                })
            }

            val requestBody = JSONObject().apply {
                put("contents", historyJson)
                put("systemInstruction", JSONObject().put("parts", JSONObject().put("text", 
                    "Ты профессиональный ИИ-помощник в P2P мессенджере. Отвечай кратко, грамотно и в неоновом стиле киберпанка.")))
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseData = response.body?.string() ?: ""
                if (!response.isSuccessful) return "⚠️ Ошибка Gemini: ${response.code}"
                
                val json = JSONObject(responseData)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    candidates.getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                } else {
                    "ИИ не смог сгенерировать ответ. Попробуйте другой запрос."
                }
            }
        } catch (e: Exception) {
            "⚠️ Ошибка соединения: ${e.localizedMessage}"
        }
    }
}
