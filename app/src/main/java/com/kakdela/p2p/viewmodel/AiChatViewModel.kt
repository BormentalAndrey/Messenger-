package com.kakdela.p2p.viewmodel

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.ai.HybridAiEngine
import com.kakdela.p2p.ai.LlamaBridge
import com.kakdela.p2p.ai.ModelDownloadManager
import com.kakdela.p2p.ai.NetworkUtils
import com.kakdela.p2p.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AiChatViewModel(app: Application) : AndroidViewModel(app) {

    private val fullHistory = mutableStateListOf<ChatMessage>()

    // Для UI отдаем только последние сообщения
    val displayMessages by derivedStateOf {
        fullHistory.takeLast(50) // Показываем больше контекста в UI, но логика гибридная
    }

    val isTyping = mutableStateOf(false)
    val isDownloading = mutableStateOf(false)
    val downloadProgress = mutableIntStateOf(0)
    
    // Флаг: файл модели физически существует
    val isModelDownloaded = mutableStateOf(false)
    // Флаг: есть интернет
    val isOnline = mutableStateOf(false)

    init {
        refreshSystemStatus()
    }

    fun refreshSystemStatus() {
        val ctx = getApplication<Application>()
        isOnline.value = NetworkUtils.isNetworkAvailable(ctx)
        
        // Проверка наличия файла
        isModelDownloaded.value = ModelDownloadManager.isInstalled(ctx)

        // Если файл есть, но модель еще не в памяти - инициализируем
        if (isModelDownloaded.value && LlamaBridge.isLibAvailable() && !LlamaBridge.isReady()) {
            initLocalModel(ctx)
        }
    }

    private fun initLocalModel(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val modelFile = ModelDownloadManager.getModelFile(context)
                // Передаем абсолютный путь в C++
                LlamaBridge.init(modelFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        fullHistory.add(ChatMessage(text = text, isMine = true))
        isTyping.value = true

        viewModelScope.launch {
            val ctx = getApplication<Application>()
            
            // Получаем ответ (логика выбора Gemini/Llama внутри)
            val response = HybridAiEngine.getResponse(ctx, text)
            
            fullHistory.add(ChatMessage(text = response, isMine = false))
            isTyping.value = false
            
            // Обновляем статус сети на случай изменений
            refreshSystemStatus()
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
                // После успешной загрузки
                isModelDownloaded.value = true
                isDownloading.value = false
                
                // Сразу инициализируем, чтобы можно было пользоваться без интернета
                initLocalModel(getApplication())
                
                fullHistory.add(ChatMessage(text = "Мозг скачан! Теперь я работаю офлайн.", isMine = false))
            } catch (e: Exception) {
                isDownloading.value = false
                fullHistory.add(ChatMessage(text = "Ошибка загрузки: ${e.message}", isMine = false))
            }
        }
    }
}
