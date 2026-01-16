package com.kakdela.p2p.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.MessageEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ChatViewModel: Обеспечивает реактивное отображение истории и управление отправкой.
 * Интегрирует IdentityRepository для сетевых операций и Room для локального хранения.
 */
class ChatViewModel(
    application: Application,
    private val repository: IdentityRepository
) : AndroidViewModel(application) {

    private val TAG = "ChatViewModel"
    private val db = ChatDatabase.getDatabase(application)
    private val messageDao = db.messageDao()
    private val nodeDao = db.nodeDao()

    private var partnerHash: String = ""
    private var partnerPhone: String? = null

    // Поток данных для UI: обновляется автоматически при изменении в БД
    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    // Слушатель входящих P2P пакетов в реальном времени
    private val p2pListener: (String, String, String, String) -> Unit = { type, data, _, fromId ->
        if (fromId == partnerHash) {
            handleIncoming(type, data, fromId)
        }
    }

    init {
        // Подписываемся на события сетевого уровня при создании ViewModel
        repository.addListener(p2pListener)
    }

    /**
     * Инициализация чата: загрузка данных партнера и подписка на поток сообщений из БД.
     */
    fun initChat(identifier: String) {
        partnerHash = identifier
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Пытаемся получить телефон партнера для SMS-фолбека
                val node = nodeDao.getNodeByHash(identifier)
                partnerPhone = node?.phone
                
                Log.d(TAG, "Chat initialized with $identifier (Phone: $partnerPhone)")

                // Подписываемся на Flow из Room. Как только в БД появится новое сообщение,
                // StateFlow обновит UI автоматически.
                messageDao.observeMessages(identifier)
                    .distinctUntilChanged()
                    .collect { list ->
                        _messages.value = list
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing chat history", e)
            }
        }
    }

    /**
     * Основной метод отправки текстового сообщения.
     * Реализует: Сохранение (UI) -> Шифрование (E2EE) -> Транспорт (P2P/SMS).
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || partnerHash.isBlank()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val myId = repository.getMyId()
                val messageId = UUID.randomUUID().toString()

                // 1. Создаем объект сообщения для локальной БД
                val msg = MessageEntity(
                    messageId = messageId,
                    chatId = partnerHash,
                    senderId = myId,
                    receiverId = partnerHash,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    isMe = true,
                    status = "PENDING" // Начальный статус
                )

                // 2. Мгновенное сохранение в БД (пользователь видит сообщение в списке)
                messageDao.insert(msg)

                // 3. Шифрование контента перед отправкой в сеть
                val peerKey = CryptoManager.getPeerPublicKey(partnerHash) ?: ""
                val encrypted = if (peerKey.isNotEmpty()) {
                    CryptoManager.encryptMessage(text, peerKey)
                } else {
                    text
                }

                // 4. Отправка через IdentityRepository (UDP или SMS)
                val isDelivered = repository.sendMessageSmart(partnerHash, partnerPhone, encrypted)

                // 5. Обновление статуса на основе результата доставки
                val finalStatus = if (isDelivered) "SENT" else "FAILED"
                messageDao.updateStatus(messageId, finalStatus)

            } catch (e: Exception) {
                Log.e(TAG, "Send failed", e)
            }
        }
    }

    /**
     * Обработка входящего P2P сообщения в контексте активного экрана чата.
     */
    private fun handleIncoming(type: String, data: String, fromId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val myId = repository.getMyId()
                
                // Дешифровка
                val text = if (type == "CHAT_MSG") {
                    CryptoManager.decryptMessage(data).ifEmpty { "[Зашифровано]" }
                } else {
                    "$type: $data"
                }

                val msg = MessageEntity(
                    messageId = UUID.randomUUID().toString(),
                    chatId = fromId,
                    senderId = fromId,
                    receiverId = myId,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    isMe = false,
                    status = "READ"
                )

                // Сохраняем в БД. StateFlow сам уведомит UI об изменениях.
                messageDao.insert(msg)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming P2P packet", e)
            }
        }
    }

    // Вспомогательные методы (расширяемо для файлов и аудио)
    fun sendFile(uri: String, fileName: String) = sendMessage("[Файл: $fileName]")
    fun sendAudio(uri: String, duration: Int) = sendMessage("[Аудиосообщение: $duration сек]")
    fun scheduleMessage(text: String, time: String) = sendMessage("[Запланировано на $time]: $text")

    override fun onCleared() {
        // Обязательно удаляем слушателя, чтобы избежать утечек памяти
        repository.removeListener(p2pListener)
        super.onCleared()
    }
}
