package com.kakdela.p2p.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.BuildConfig
import com.kakdela.p2p.ui.chat.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AiChatViewModel : ViewModel() {

    private val _messages = mutableStateListOf(
        ChatMessage(text = "Привет! Я твой продвинутый ИИ-ассистент. Чем могу помочь?", isUser = false)
    )
    val messages: SnapshotStateList<ChatMessage> get() = _messages
    
    // Состояние загрузки (индикатор печати)
    var isTyping = mutableStateOf(false)
        private set

    private val client = OkHttpClient()

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        _messages.add(ChatMessage(text = text, isUser = true))
        isTyping.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val reply = askGeminiWithHistory(text)
            withContext(Dispatchers.Main) {
                isTyping.value = false
                _messages.add(ChatMessage(text = reply, isUser = false))
            }
        }
    }

    private suspend fun askGeminiWithHistory(prompt: String): String {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) return "❌ Настройте GEMINI_API_KEY"

        val historyJson = JSONArray()
        
        // Берем последние 10 сообщений для контекста
        _messages.takeLast(10).forEach { msg ->
            historyJson.put(JSONObject().apply {
                put("role", if (msg.isUser) "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
            })
        }

        val requestBody = JSONObject().apply {
            put("contents", historyJson)
            // Добавляем системную инструкцию (делает ИИ более "реальным")
            put("systemInstruction", JSONObject().put("parts", JSONObject().put("text", 
                "Ты профессиональный и дружелюбный ассистент в приложении P2P Messenger. Отвечай кратко и по делу.")))
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) return "⚠️ Ошибка Gemini (${response.code})"
                
                val json = JSONObject(body)
                json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            }
        } catch (e: Exception) {
            "⚠️ Ошибка сети: ${e.localizedMessage}"
        }
    }
}
