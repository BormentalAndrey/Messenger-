package com.kakdela.p2p.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.ai.AiMemoryStore
import com.kakdela.p2p.ai.LlamaBridge
import com.kakdela.p2p.ai.ModelDownloadManager
import com.kakdela.p2p.ai.RagEngine
import com.kakdela.p2p.ai.WebSearcher
import com.kakdela.p2p.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AiChatViewModel(app: Application) : AndroidViewModel(app) {

    val messages = mutableStateListOf<ChatMessage>()
    val isTyping = mutableStateOf(false)
    val isDownloading = mutableStateOf(false)
    val downloadProgress = mutableIntStateOf(0)
    
    // Проверка состояния при старте
    val modelReady = mutableStateOf(ModelDownloadManager.isInstalled(app))

    init {
        initializeSystem()
    }

    private fun initializeSystem() {
        if (modelReady.value) {
            try {
                LlamaBridge.init(ModelDownloadManager.modelFile(getApplication()).absolutePath)
                messages.add(ChatMessage("Система готова. Чем могу помочь?", false))
            } catch (e: Exception) {
                messages.add(ChatMessage("Ошибка инициализации ядра: ${e.message}", false))
            }
        } else {
            messages.add(ChatMessage("Требуется загрузка AI модели (~2.4 GB). Нажмите кнопку загрузки.", false))
        }
    }

    fun downloadModel() {
        if (isDownloading.value) return
        
        isDownloading.value = true
        viewModelScope.launch {
            try {
                ModelDownloadManager.download(getApplication()) { progress ->
                    downloadProgress.intValue = progress
                }
                modelReady.value = true
                LlamaBridge.init(ModelDownloadManager.modelFile(getApplication()).absolutePath)
                messages.add(ChatMessage("Модель успешно установлена! Можно общаться.", false))
            } catch (e: Exception) {
                messages.add(ChatMessage("Ошибка загрузки: ${e.localizedMessage}", false))
            } finally {
                isDownloading.value = false
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        // 1. Отображаем сообщение юзера
        messages.add(ChatMessage(text, true))
        
        if (!modelReady.value) {
            messages.add(ChatMessage("Сначала скачайте модель.", false))
            return
        }

        isTyping.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 2. Сбор контекста
                AiMemoryStore.remember(text)
                val relevantDocs = RagEngine.relevant(text)
                
                // 3. Поиск в сети (параллельно или последовательно)
                val webInfo = WebSearcher.search(text)
                if (webInfo.isNotBlank()) {
                    RagEngine.addWeb(webInfo)
                }

                // 4. Формирование промпта (формат Phi-3)
                // Очень важно использовать правильные теги для конкретной модели
                val contextBlock = buildString {
                    if (relevantDocs.isNotBlank()) append("Relevant Info: $relevantDocs\n")
                    if (webInfo.isNotBlank()) append("Web Info: $webInfo\n")
                    append("History:\n${AiMemoryStore.context()}")
                }

                val fullPrompt = """
                    <|user|>
                    Context:
                    $contextBlock
                    
                    Question: $text
                    <|end|>
                    <|assistant|>
                """.trimIndent()

                // 5. Генерация
                val response = LlamaBridge.prompt(fullPrompt)
                
                // Очистка ответа от системных тегов, если они пролезли
                val cleanResponse = response
                    .replace("<|assistant|>", "")
                    .replace("<|end|>", "")
                    .trim()

                // 6. UI Update
                messages.add(ChatMessage(cleanResponse, false))
                AiMemoryStore.remember(cleanResponse) // Запоминаем свой ответ тоже

            } catch (e: Exception) {
                e.printStackTrace()
                messages.add(ChatMessage("Ошибка генерации: ${e.message}", false))
            } finally {
                isTyping.value = false
            }
        }
    }
}
