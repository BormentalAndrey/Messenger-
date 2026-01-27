package com.kakdela.p2p.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.kakdela.p2p.ai.LlamaBridge
import com.kakdela.p2p.ai.ModelDownloadManager
import com.kakdela.p2p.ai.AiMemoryStore
import com.kakdela.p2p.ai.RagEngine
import com.kakdela.p2p.ai.WebSearcher
import com.kakdela.p2p.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AiChatViewModel(app: Application) : AndroidViewModel(app) {

    // ==========================
    // Состояние чата
    // ==========================
    val messages = mutableStateListOf<ChatMessage>()
    val isTyping = mutableStateOf(false)
    val downloadProgress = mutableStateOf(0)
    val modelReady = mutableStateOf(ModelDownloadManager.isInstalled(app))

    init {
        // Инициализация сообщений
        messages += ChatMessage(
            if (modelReady.value)
                "Локальный ИИ готов."
            else
                "Для работы нужно скачать модель.",
            false
        )

        // Если модель уже есть — инициализируем llama
        if (modelReady.value) {
            LlamaBridge.init(
                ModelDownloadManager.modelFile(app).absolutePath
            )
        }
    }

    // ==========================
    // Загрузка модели
    // ==========================
    fun downloadModel() {
        viewModelScope.launch {
            ModelDownloadManager.download(getApplication()) { progress ->
                downloadProgress.value = progress
            }
            modelReady.value = true
            LlamaBridge.init(
                ModelDownloadManager.modelFile(getApplication()).absolutePath
            )
            messages += ChatMessage("Модель успешно загружена и готова к работе!", false)
        }
    }

    // ==========================
    // Отправка сообщения
    // ==========================
    fun send(text: String) {
        if (!modelReady.value) {
            messages += ChatMessage("Модель ещё не готова. Скачайте её.", false)
            return
        }

        // Добавляем сообщение пользователя
        messages += ChatMessage(text, true)
        isTyping.value = true

        viewModelScope.launch(Dispatchers.IO) {

            try {
                // ----------------------
                // 1️⃣ Локальная память
                // ----------------------
                AiMemoryStore.remember(text)
                RagEngine.addLocal(text)

                // ----------------------
                // 2️⃣ Интернет-поиск
                // ----------------------
                val webData = try { WebSearcher.search(text) } catch (e: Exception) { "" }
                if (webData.isNotBlank()) RagEngine.addWeb(webData)

                // ----------------------
                // 3️⃣ Формируем промпт для локальной модели
                // ----------------------
                val prompt = """
                    Память:
                    ${AiMemoryStore.context()}

                    Релевантные данные:
                    ${RagEngine.relevant(text)}

                    Запрос пользователя:
                    $text
                """.trimIndent()

                // ----------------------
                // 4️⃣ Получаем ответ от локальной модели
                // ----------------------
                val reply = LlamaBridge.prompt(prompt)

                // ----------------------
                // 5️⃣ Обновляем UI
                // ----------------------
                isTyping.value = false
                messages += ChatMessage(reply, false)

            } catch (e: Exception) {
                isTyping.value = false
                messages += ChatMessage("Ошибка ИИ: ${e.message}", false)
            }
        }
    }
}
