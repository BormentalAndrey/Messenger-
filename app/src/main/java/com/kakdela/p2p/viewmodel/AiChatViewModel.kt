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
import kotlinx.coroutines.withContext

class AiChatViewModel(app: Application) : AndroidViewModel(app) {

    // Вся история чата хранится здесь, но скрыта от UI
    private val fullHistory = mutableStateListOf<ChatMessage>()

    // UI видит только последние 2 сообщения: Вопрос и Ответ
    val displayMessages by derivedStateOf {
        fullHistory.takeLast(2)
    }

    val isTyping = mutableStateOf(false)
    val isDownloading = mutableStateOf(false)
    val downloadProgress = mutableIntStateOf(0)
    val modelReady = mutableStateOf(false)
    val isOnline = mutableStateOf(false) // Индикатор для UI

    init {
        checkSystemStatus()
    }

    private fun checkSystemStatus() {
        val ctx = getApplication<Application>()
        isOnline.value = NetworkUtils.isNetworkAvailable(ctx)
        modelReady.value = ModelDownloadManager.isInstalled(ctx)
        
        if (modelReady.value) {
            // Инициализация нативного моста в фоне
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val path = ModelDownloadManager.modelFile(ctx).absolutePath
                    LlamaBridge.init(path)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        // Добавляем вопрос пользователя
        fullHistory.add(ChatMessage(text = text, isMine = true))
        isTyping.value = true

        viewModelScope.launch {
            val ctx = getApplication<Application>()
            
            // Получаем ответ от Гибридного движка (Gemini или Local)
            val response = HybridAiEngine.getResponse(ctx, text)
            
            fullHistory.add(ChatMessage(text = response, isMine = false))
            
            isTyping.value = false
            // Обновляем статус сети для актуальности UI
            isOnline.value = NetworkUtils.isNetworkAvailable(ctx)
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
                checkSystemStatus() // Инициализация после загрузки
            } catch (e: Exception) {
                fullHistory.add(ChatMessage(text = "Ошибка загрузки: ${e.message}", isMine = false))
            } finally {
                isDownloading.value = false
            }
        }
    }
}
