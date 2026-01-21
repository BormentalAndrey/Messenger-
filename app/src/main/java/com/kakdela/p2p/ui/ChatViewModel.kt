package com.kakdela.p2p.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.MessageRepository
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ChatViewModel: Управляет UI-состоянием чата.
 * Использует MessageRepository для всех операций с данными.
 *
 * Исправления для совместимости с NavGraph:
 * - sendFile теперь принимает только Uri и fileName (type определяется внутри или хардкод).
 * - Добавлен sendAudioMessage(Uri, fileName) — отправка аудио как файл с type = "audio".
 * - scheduleMessage принимает timestamp: Long.
 */
class ChatViewModel(
    application: Application,
    private val identityRepo: IdentityRepository,
    private val messageRepo: MessageRepository
) : AndroidViewModel(application) {

    private val TAG = "ChatViewModel"
    private val db = ChatDatabase.getDatabase(application)
    private val messageDao = db.messageDao()

    private var partnerHash: String = ""

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    /**
     * Инициализация чата и подписка на обновления базы данных.
     */
    fun initChat(identifier: String) {
        partnerHash = identifier
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.observeMessages(identifier)
                .distinctUntilChanged()
                .collect { list ->
                    _messages.value = list.sortedBy { it.timestamp } // Сортировка по времени для правильного порядка
                }
        }
    }

    /**
     * Отправка текста
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || partnerHash.isBlank()) return
        messageRepo.sendText(partnerHash, text)
    }

    /**
     * Отправка файла (универсальный метод)
     * type определяется автоматически или хардкодится ("file"/"image"/"audio" и т.д.)
     */
    fun sendFile(uri: Uri, fileName: String) {
        if (partnerHash.isBlank()) return
        // Здесь можно добавить определение mime-type если нужно
        val type = when {
            fileName.endsWith(".jpg", ignoreCase = true) ||
            fileName.endsWith(".jpeg", ignoreCase = true) ||
            fileName.endsWith(".png", ignoreCase = true) -> "image"
            fileName.endsWith(".mp3", ignoreCase = true) ||
            fileName.endsWith(".wav", ignoreCase = true) ||
            fileName.endsWith(".m4a", ignoreCase = true) -> "audio"
            else -> "file"
        }
        messageRepo.sendFile(partnerHash, uri, type, fileName)
    }

    /**
     * Отправка аудиосообщения (использует sendFile с type = "audio")
     */
    fun sendAudioMessage(uri: Uri, fileName: String) {
        if (partnerHash.isBlank()) return
        messageRepo.sendFile(partnerHash, uri, "audio", fileName)
    }

    /**
     * Планирование сообщения
     */
    fun scheduleMessage(text: String, timestamp: Long) {
        if (text.isBlank() || partnerHash.isBlank()) return
        messageRepo.sendText(partnerHash, text, scheduledTime = timestamp)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
