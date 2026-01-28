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

    // Список сообщений для UI (Compose отслеживает изменения автоматически)
    val messages = mutableStateListOf<ChatMessage>()
    
    // Состояния интерфейса
    val isTyping = mutableStateOf(false)
    val isDownloading = mutableStateOf(false)
    val downloadProgress = mutableIntStateOf(0)
    
    // Проверка наличия модели на диске при старте
    val modelReady = mutableStateOf(ModelDownloadManager.isInstalled(app))

    init {
        // Запуск инициализации в фоновом режиме, чтобы не блокировать Main Thread при старте
        viewModelScope.launch(Dispatchers.IO) {
            initializeSystem()
        }
    }

    private suspend fun initializeSystem() {
        if (modelReady.value) {
            try {
                val modelFile = ModelDownloadManager.modelFile(getApplication())
                if (!modelFile.exists()) {
                    throw IllegalStateException("Файл модели не найден по пути: ${modelFile.absolutePath}")
                }

                // Инициализация нативного движка
                LlamaBridge.init(modelFile.absolutePath)

                withContext(Dispatchers.Main) {
                    messages.add(ChatMessage(text = "Система ИИ готова к работе.", isMine = false))
                }
            } catch (t: Throwable) {
                // Ловим Throwable, так как JNI может кинуть UnsatisfiedLinkError
                withContext(Dispatchers.Main) {
                    messages.add(ChatMessage(text = "Ошибка инициализации ядра: ${t.localizedMessage}", isMine = false))
                    modelReady.value = false
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                messages.add(ChatMessage(text = "Для работы требуется локальная модель (~2.4 ГБ).", isMine = false))
            }
        }
    }

    fun downloadModel() {
        if (isDownloading.value) return
        
        isDownloading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Процесс загрузки
                ModelDownloadManager.download(getApplication()) { progress ->
                    // Обновляем прогресс на UI
                    viewModelScope.launch(Dispatchers.Main) {
                        downloadProgress.intValue = progress
                    }
                }

                // Проверка после загрузки
                val modelFile = ModelDownloadManager.modelFile(getApplication())
                if (modelFile.exists()) {
                    LlamaBridge.init(modelFile.absolutePath)
                    
                    withContext(Dispatchers.Main) {
                        modelReady.value = true
                        messages.add(ChatMessage(text = "Модель успешно установлена и готова!", isMine = false))
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    messages.add(ChatMessage(text = "Ошибка загрузки: ${t.localizedMessage}", isMine = false))
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isDownloading.value = false
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val userText = text.trim()
        if (userText.isBlank()) return
        
        // 1. Отображаем сообщение пользователя
        messages.add(ChatMessage(text = userText, isMine = true))
        
        if (!modelReady.value) {
            messages.add(ChatMessage(text = "Пожалуйста, сначала скачайте модель.", isMine = false))
            return
        }

        isTyping.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 2. RAG и Память
                AiMemoryStore.remember(userText)
                val relevantDocs = RagEngine.relevant(userText)
                
                // 3. Веб-поиск (с обработкой ошибок сети)
                val webInfo = try { 
                    WebSearcher.search(userText) 
                } catch (e: Exception) { 
                    "" 
                }
                
                if (webInfo.isNotBlank()) {
                    RagEngine.addWeb(webInfo)
                }

                // 4. Формирование контекста и промпта
                val contextBlock = buildString {
                    if (relevantDocs.isNotBlank()) append("Local Documents:\n$relevantDocs\n")
                    if (webInfo.isNotBlank()) append("Latest Web Data:\n$webInfo\n")
                    val history = AiMemoryStore.context()
                    if (history.isNotBlank()) append("Chat History:\n$history\n")
                }

                val fullPrompt = """
                    <|user|>
                    $contextBlock
                    Question: $userText
                    <|end|>
                    <|assistant|>
                """.trimIndent()

                // 5. Генерация через LlamaBridge
                val rawResponse = LlamaBridge.prompt(fullPrompt)
                
                // Глубокая очистка ответа от технических тегов
                val cleanResponse = rawResponse
                    .replace("<|assistant|>", "")
                    .replace("<|end|>", "")
                    .replace("<|user|>", "")
                    .replace("<|system|>", "")
                    .trim()

                // 6. Финализация ответа в UI
                withContext(Dispatchers.Main) {
                    if (cleanResponse.isNotBlank()) {
                        messages.add(ChatMessage(text = cleanResponse, isMine = false))
                        AiMemoryStore.remember(cleanResponse)
                    } else {
                        messages.add(ChatMessage(text = "ИИ не смог сгенерировать ответ. Попробуйте другой вопрос.", isMine = false))
                    }
                }

            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    messages.add(ChatMessage(text = "Критическая ошибка: ${t.localizedMessage}", isMine = false))
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isTyping.value = false
                }
            }
        }
    }
}
