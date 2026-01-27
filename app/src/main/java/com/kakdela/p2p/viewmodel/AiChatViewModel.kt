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
import kotlinx.coroutines.withContext

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
                messages.add(ChatMessage(text = "Система готова. Чем могу помочь?", isMine = false))
            } catch (e: Exception) {
                messages.add(ChatMessage(text = "Ошибка инициализации ядра: ${e.message}", isMine = false))
            }
        } else {
            messages.add(ChatMessage(text = "Требуется загрузка AI модели (~2.4 GB). Нажмите кнопку загрузки.", isMine = false))
        }
    }

    fun downloadModel() {
        if (isDownloading.value) return
        
        isDownloading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ModelDownloadManager.download(getApplication()) { progress ->
                        downloadProgress.intValue = progress
                    }
                }
                modelReady.value = true
                LlamaBridge.init(ModelDownloadManager.modelFile(getApplication()).absolutePath)
                messages.add(ChatMessage(text = "Модель успешно установлена! Можно общаться.", isMine = false))
            } catch (e: Exception) {
                messages.add(ChatMessage(text = "Ошибка загрузки: ${e.localizedMessage}", isMine = false))
            } finally {
                isDownloading.value = false
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        // 1. Отображаем сообщение юзера (используем именованные аргументы под вашу модель)
        messages.add(ChatMessage(text = text, isMine = true))
        
        if (!modelReady.value) {
            messages.add(ChatMessage(text = "Сначала скачайте модель.", isMine = false))
            return
        }

        isTyping.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 2. Сбор контекста
                AiMemoryStore.remember(text)
                val relevantDocs = RagEngine.relevant(text)
                
                // 3. Поиск в сети
                val webInfo = try { WebSearcher.search(text) } catch (e: Exception) { "" }
                if (webInfo.isNotBlank()) {
                    RagEngine.addWeb(webInfo)
                }

                // 4. Формирование промпта (формат Phi-3)
                val contextBlock = buildString {
                    if (relevantDocs.isNotBlank()) append("Relevant Info: $relevantDocs\n")
                    if (webInfo.isNotBlank()) append("Web Info: $webInfo\n")
                    val history = AiMemoryStore.context()
                    if (history.isNotBlank()) append("History:\n$history")
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
                
                // Очистка ответа от системных тегов
                val cleanResponse = response
                    .replace("<|assistant|>", "")
                    .replace("<|end|>", "")
                    .replace("<|user|>", "")
                    .trim()

                // 6. UI Update (переключаемся на Main для обновления списка)
                withContext(Dispatchers.Main) {
                    messages.add(ChatMessage(text = cleanResponse, isMine = false))
                }
                
                AiMemoryStore.remember(cleanResponse) // Запоминаем свой ответ тоже

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    messages.add(ChatMessage(text = "Ошибка генерации: ${e.message}", isMine = false))
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isTyping.value = false
                }
            }
        }
    }
}
