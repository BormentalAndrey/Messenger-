package com.kakdela.p2p.viewmodel

import android.app.Application
import android.util.Log
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

    val displayMessages by derivedStateOf {
        fullHistory.takeLast(50)
    }

    // Состояния UI
    val isTyping = mutableStateOf(false) // ИИ думает
    val isDownloading = mutableStateOf(false) // Идет загрузка файла
    val downloadProgress = mutableIntStateOf(0)
    
    val isModelDownloaded = mutableStateOf(false)
    val isOnline = mutableStateOf(false)

    init {
        refreshSystemStatus()
    }

    fun refreshSystemStatus() {
        val ctx = getApplication<Application>()
        isOnline.value = NetworkUtils.isNetworkAvailable(ctx)
        
        // Проверяем физическое наличие файла
        val installed = ModelDownloadManager.isInstalled(ctx)
        isModelDownloaded.value = installed

        // Если файл есть, и либа загружена, но еще не инитнули -> инитим
        if (installed && LlamaBridge.isLibAvailable() && !LlamaBridge.isReady()) {
            initLocalModel(ctx)
        }
    }

    private fun initLocalModel(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val modelFile = ModelDownloadManager.getModelFile(context)
                if (modelFile.exists()) {
                    LlamaBridge.init(modelFile.absolutePath)
                    Log.d("AiVM", "Local Llama initialized")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        // Добавляем сообщение юзера сразу
        fullHistory.add(ChatMessage(text = text, isMine = true))
        isTyping.value = true

        viewModelScope.launch {
            val ctx = getApplication<Application>()
            
            // Обновляем статус сети перед запросом
            isOnline.value = NetworkUtils.isNetworkAvailable(ctx)

            // Запрашиваем ответ. 
            // Это работает параллельно с загрузкой, если есть интернет.
            val response = HybridAiEngine.getResponse(ctx, text)
            
            fullHistory.add(ChatMessage(text = response, isMine = false))
            isTyping.value = false
        }
    }

    fun downloadModel() {
        if (isDownloading.value) return // Защита от двойного нажатия
        
        isDownloading.value = true
        downloadProgress.intValue = 0
        
        viewModelScope.launch {
            try {
                fullHistory.add(ChatMessage(text = "Начинаю загрузку локального мозга...", isMine = false))
                
                ModelDownloadManager.download(getApplication()) { progress ->
                    downloadProgress.intValue = progress
                }
                
                // Успех
                isModelDownloaded.value = true
                isDownloading.value = false
                fullHistory.add(ChatMessage(text = "Загрузка завершена! Инициализирую...", isMine = false))
                
                // Инициализация
                initLocalModel(getApplication())
                
            } catch (e: Exception) {
                isDownloading.value = false
                downloadProgress.intValue = 0
                fullHistory.add(ChatMessage(text = "Ошибка загрузки: ${e.message}", isMine = false))
                Log.e("AiVM", "Download error", e)
            }
        }
    }
}
