package com.kakdela.p2p.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.kakdela.p2p.ai.LlamaBridge
import com.kakdela.p2p.ai.ModelDownloadManager
import com.kakdela.p2p.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AiChatViewModel(app: Application) : AndroidViewModel(app) {

    val messages = mutableStateListOf<ChatMessage>()
    val isTyping = mutableStateOf(false)
    val downloadProgress = mutableStateOf(0)
    val modelReady = mutableStateOf(
        ModelDownloadManager.isInstalled(app)
    )

    init {
        messages += ChatMessage(
            if (modelReady.value)
                "Локальный ИИ готов."
            else
                "Для работы нужно скачать модель.",
            false
        )

        if (modelReady.value) {
            LlamaBridge.init(
                ModelDownloadManager.modelFile(app).absolutePath
            )
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            ModelDownloadManager.download(getApplication()) {
                downloadProgress.value = it
            }
            modelReady.value = true
            LlamaBridge.init(
                ModelDownloadManager.modelFile(getApplication()).absolutePath
            )
        }
    }

    fun send(text: String) {
        if (!modelReady.value) return

        messages += ChatMessage(text, true)
        isTyping.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val reply = LlamaBridge.prompt(text)
            isTyping.value = false
            messages += ChatMessage(reply, false)
        }
    }
}
