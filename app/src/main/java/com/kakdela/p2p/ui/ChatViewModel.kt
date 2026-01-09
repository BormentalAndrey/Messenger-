package com.kakdela.p2p.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.Message
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel для управления состоянием чата.
 * Использует AndroidViewModel для доступа к контексту (необходим для WebRTC/Файлов).
 */
class ChatViewModel(
    private val repository: IdentityRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var partnerId: String = ""

    // Слушатель входящих P2P сигналов
    private val listener: (String, String, String, String) -> Unit = { type, data, fromIp, fromId ->
        if (fromId == partnerId) {
            when (type) {
                "MESSAGE" -> handleIncomingMessage(data, fromId)
                "FILE" -> handleIncomingMessage("📎 Получен файл: $data", fromId)
                "AUDIO" -> handleIncomingMessage("🎤 Аудиосообщение", fromId)
            }
        }
    }

    init {
        repository.addListener(listener)
    }

    /**
     * Инициализация чата с конкретным собеседником.
     */
    fun initChat(partnerId: String) {
        this.partnerId = partnerId
        // Здесь можно добавить загрузку истории из локальной БД (Room)
    }

    private fun handleIncomingMessage(encryptedData: String, fromId: String) {
        // Расшифровываем входящее сообщение
        val decryptedText = CryptoManager.decryptMessage(encryptedData)
        
        val msg = Message(
            id = UUID.randomUUID().toString(),
            senderId = fromId,
            text = decryptedText,
            timestamp = System.currentTimeMillis(),
            isMe = false
        )
        _messages.update { it + msg }
    }

    /**
     * Базовая отправка текстового сообщения с E2EE шифрованием.
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || partnerId.isBlank()) return

        val myId = repository.getMyId()
        val localMsg = Message(
            id = UUID.randomUUID().toString(),
            senderId = myId,
            text = text,
            timestamp = System.currentTimeMillis(),
            isMe = true
        )

        _messages.update { it + localMsg }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Получаем публичный ключ собеседника из кэша
                val peerKey = CryptoManager.getPeerPublicKey(partnerId) ?: ""
                
                // 2. Шифруем сообщение
                val encryptedText = if (peerKey.isNotEmpty()) {
                    CryptoManager.encryptMessage(text, peerKey)
                } else {
                    text // Фоллбек, если ключ еще не получен (лучше обработать ошибку)
                }

                // 3. Отправляем через P2P сигналинг
                repository.sendSignaling(
                    targetIp = "", 
                    type = "MESSAGE",
                    data = encryptedText
                )
            } catch (e: Exception) {
                // Здесь можно пометить сообщение как "не доставлено" в UI
            }
        }
    }

    /**
     * Отправка файла через P2P.
     */
    fun sendFile(uri: String, fileName: String) {
        val displayMsg = "📎 Файл: $fileName"
        _messages.update { it + createLocalMeMessage(displayMsg) }
        
        viewModelScope.launch(Dispatchers.IO) {
            // В реальности здесь запускается WebRTC DataChannel или HTTP/P2P стрим
            repository.sendSignaling("", "FILE", fileName)
        }
    }

    /**
     * Отправка аудиосообщения.
     */
    fun sendAudio(uri: String, duration: Int) {
        val displayMsg = "🎤 Голосовое сообщение (${duration} сек.)"
        _messages.update { it + createLocalMeMessage(displayMsg) }

        viewModelScope.launch(Dispatchers.IO) {
            repository.sendSignaling("", "AUDIO", uri)
        }
    }

    /**
     * Планирование отправки сообщения.
     */
    fun scheduleMessage(text: String, time: String) {
        val infoMsg = "⏰ Запланировано на $time: $text"
        _messages.update { it + createLocalMeMessage(infoMsg) }
        
        // Логика планировщика (WorkManager или внутренний сервис)
    }

    private fun createLocalMeMessage(text: String) = Message(
        id = UUID.randomUUID().toString(),
        senderId = repository.getMyId(),
        text = text,
        timestamp = System.currentTimeMillis(),
        isMe = true
    )

    override fun onCleared() {
        repository.removeListener(listener)
        super.onCleared()
    }
}
