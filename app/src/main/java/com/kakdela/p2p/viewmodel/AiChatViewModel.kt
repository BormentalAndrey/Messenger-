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

    // Список сообщений для UI
    val messages = mutableStateListOf<ChatMessage>()
    
    // Состояния интерфейса
    val isTyping = mutableStateOf(false)
    val isDownloading = mutableStateOf(false)
    val downloadProgress = mutableIntStateOf(0)
    val modelReady = mutableStateOf(ModelDownloadManager.isInstalled(app))

    init {
        initializeSystem()
    }

    /**
     * Начальная инициализация при запуске ViewModel
     */
    private fun initializeSystem() {
        if (modelReady.value) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val file = ModelDownloadManager.modelFile(getApplication())
                    if (file.exists()) {
                        LlamaBridge.init(file.absolutePath)
                        withContext(Dispatchers.Main) {
                            messages.add(ChatMessage(text = "Система готова. Чем могу помочь?", isMine = false))
                        }
                    } else {
                        throw Exception("Файл модели отсутствует по пути: ${file.path}")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage(text = "Ошибка ядра: ${e.localizedMessage}", isMine = false))
                        modelReady.value = false // Сбрасываем флаг, если файл битый
                    }
                }
            }
        } else {
            messages.add(ChatMessage(text = "Требуется загрузка AI модели (~2.4 GB).", isMine = false))
        }
    }

    /**
     * Загрузка модели из Hugging Face
     */
    fun downloadModel() {
        if (isDownloading.value) return
        
        isDownloading.value = true
        viewModelScope.launch(Dispatchers.Main) {
            try {
                // Выполняем загрузку в IO потоке
                withContext(Dispatchers.IO) {
                    ModelDownloadManager.download(getApplication()) { progress ->
                        // Обновляем прогресс на Main Thread
                        viewModelScope.launch(Dispatchers.Main) {
                            downloadProgress.intValue = progress
                        }
                    }
                }
                
                // После успешной загрузки инициализируем мост
                val modelPath = ModelDownloadManager.modelFile(getApplication()).absolutePath
                withContext(Dispatchers.IO) {
                    LlamaBridge.init(modelPath)
                }
                
                modelReady.value = true
                messages.add(ChatMessage(text = "Модель успешно установлена! Можно общаться.", isMine = false))
            } catch (e: Exception) {
                messages.add(ChatMessage(text = "Ошибка загрузки: ${e.localizedMessage}", isMine = false))
            } finally {
                isDownloading.value = false
            }
        }
    }

    /**
     * Логика отправки сообщения и генерации ответа
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        // 1. Добавляем сообщение пользователя в UI (Main Thread)
        messages.add(ChatMessage(text = text, isMine = true))
        
        if (!modelReady.value) {
            messages.add(ChatMessage(text = "Сначала скачайте модель.", isMine = false))
            return
        }

        isTyping.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 2. Работа с памятью и RAG
                AiMemoryStore.remember(text)
                val relevantDocs = RagEngine.relevant(text)
                
                // 3. Веб-поиск (опционально)
                val webInfo = try { WebSearcher.search(text) } catch (e: Exception) { "" }
                if (webInfo.isNotBlank()) {
                    RagEngine.addWeb(webInfo)
                }

                // 4. Сборка промпта для Phi-3
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

                // 5. Вызов нативного метода генерации
                val response = LlamaBridge.prompt(fullPrompt)
                
                // Очистка ответа от системных токенов
                val cleanResponse = response
                    .replace("<|assistant|>", "")
                    .replace("<|end|>", "")
                    .replace("<|user|>", "")
                    .trim()

                // 6. Обновление UI и сохранение в память
                withContext(Dispatchers.Main) {
                    messages.add(ChatMessage(text = cleanResponse, isMine = false))
                    AiMemoryStore.remember(cleanResponse)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    messages.add(ChatMessage(text = "Ошибка ИИ: ${e.localizedMessage}", isMine = false))
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isTyping.value = false
                }
            }
        }
    }
}
