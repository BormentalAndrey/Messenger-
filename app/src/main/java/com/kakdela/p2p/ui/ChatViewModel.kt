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
 */
class ChatViewModel(
    application: Application,
    private val identityRepo: IdentityRepository,
    private val messageRepo: MessageRepository // Добавлен в конструктор через DI (Koin/Manual)
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
            // Подписка на Flow: UI обновится сам, когда MessageRepository 
            // запишет входящее или исходящее сообщение в БД.
            messageDao.observeMessages(identifier)
                .distinctUntilChanged()
                .collect { list ->
                    _messages.value = list
                }
        }
    }

    /**
     * Отправка текста: делегируем репозиторию.
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || partnerHash.isBlank()) return
        messageRepo.sendText(partnerHash, text)
    }

    /**
     * Отправка файла: делегируем репозиторию.
     */
    fun sendFile(uri: Uri, type: String, fileName: String) {
        if (partnerHash.isBlank()) return
        messageRepo.sendFile(partnerHash, uri, type, fileName)
    }

    /**
     * Планирование сообщения.
     */
    fun scheduleMessage(text: String, timestamp: Long) {
        if (text.isBlank() || partnerHash.isBlank()) return
        messageRepo.sendText(partnerHash, text, scheduledTime = timestamp)
    }

    override fun onCleared() {
        // Очистка ресурсов если необходимо
        super.onCleared()
    }
}
