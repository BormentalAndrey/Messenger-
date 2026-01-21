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
 * Поддерживает как P2P хеши, так и номера телефонов.
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
     * Инициализация чата.
     * identifier может быть хэшем пользователя или номером телефона.
     */
    fun initChat(identifier: String) {
        partnerHash = identifier
        viewModelScope.launch(Dispatchers.IO) {
            // Подписываемся на сообщения из БД.
            // Если это новый SMS чат, список может быть пуст, пока мы не отправим первое сообщение
            // или пока система не импортирует SMS (реализовано в MessageRepository/IncomingReceiver).
            messageDao.observeMessages(identifier)
                .distinctUntilChanged()
                .collect { list ->
                    _messages.value = list.sortedBy { it.timestamp }
                }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || partnerHash.isBlank()) return
        // MessageRepository сам решит: шифровать (P2P) или нет (SMS) в зависимости от partnerHash
        messageRepo.sendText(partnerHash, text)
    }

    fun sendFile(uri: Uri, fileName: String) {
        if (partnerHash.isBlank()) return
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

    fun sendAudioMessage(uri: Uri, fileName: String) {
        if (partnerHash.isBlank()) return
        messageRepo.sendFile(partnerHash, uri, "audio", fileName)
    }

    fun scheduleMessage(text: String, timestamp: Long) {
        if (text.isBlank() || partnerHash.isBlank()) return
        messageRepo.sendText(partnerHash, text, scheduledTime = timestamp)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
