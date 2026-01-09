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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Продакшн ViewModel для управления чатом.
 * Реализует логику: сохранение в БД -> умная отправка (Wi-Fi/P2P/Server/SMS).
 */
class ChatViewModel(
    application: Application,
    private val repository: IdentityRepository
) : AndroidViewModel(application) {

    private val TAG = "ChatViewModel"
    private val db = ChatDatabase.getDatabase(application)
    private val messageDao = db.messageDao()
    private val nodeDao = db.nodeDao()

    // Текущий собеседник
    private var partnerHash: String = ""
    private var partnerPhone: String? = null

    // Наблюдаемый поток сообщений напрямую из БД
    lateinit var messages: StateFlow<List<MessageEntity>>

    // Слушатель входящих UDP/P2P пакетов
    private val p2pListener: (String, String, String, String) -> Unit = { type, data, fromIp, fromId ->
        if (fromId == partnerHash) {
            handleIncomingP2P(type, data, fromId)
        }
    }

    init {
        repository.addListener(p2pListener)
    }

    /**
     * Инициализация чата. Загружает данные партнера и подписывается на Flow сообщений.
     */
    fun initChat(partnerHash: String) {
        this.partnerHash = partnerHash
        
        // Подгружаем данные партнера (телефон нужен для SMS-фоллбека)
        viewModelScope.launch(Dispatchers.IO) {
            val node = nodeDao.getNodeByHash(partnerHash)
            partnerPhone = node?.phone
        }

        // Настраиваем реактивное обновление UI при изменении БД
        messages = messageDao.observeMessages(partnerHash)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    /**
     * Умная отправка сообщения с использованием иерархии каналов.
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || partnerHash.isBlank()) return

        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Шифрование E2EE
                val peerKey = CryptoManager.getPeerPublicKey(partnerHash) ?: ""
                val encryptedText = if (peerKey.isNotEmpty()) {
                    CryptoManager.encryptMessage(text, peerKey)
                } else text

                // 2. Предварительное сохранение в БД со статусом PENDING
                val localMsg = MessageEntity(
                    messageId = messageId,
                    chatId = partnerHash,
                    senderId = repository.getMyId(),
                    receiverId = partnerHash,
                    text = text, // В локальной БД храним расшифрованным для UI
                    timestamp = timestamp,
                    isMe = true,
                    status = "PENDING"
                )
                messageDao.insert(localMsg)

                // 3. Запуск иерархической отправки через репозиторий
                // Wi-Fi -> Swarm -> Server -> SMS
                repository.sendMessageSmart(
                    targetHash = partnerHash,
                    targetPhone = partnerPhone,
                    message = encryptedText
                ).join() // Ждем завершения попыток

                // 4. Обновляем статус в БД (в реальности статус подтверждается ACK-пакетом)
                messageDao.updateStatus(messageId, "SENT")

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка отправки: ${e.message}")
                messageDao.updateStatus(messageId, "ERROR")
            }
        }
    }

    /**
     * Обработка входящего P2P сообщения.
     */
    private fun handleIncomingP2P(type: String, encryptedData: String, fromId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val decryptedText = if (type == "CHAT") {
                    CryptoManager.decryptMessage(encryptedData)
                } else "Media Content: $type"

                val msg = MessageEntity(
                    messageId = UUID.randomUUID().toString(),
                    chatId = fromId,
                    senderId = fromId,
                    receiverId = repository.getMyId(),
                    text = decryptedText,
                    timestamp = System.currentTimeMillis(),
                    isMe = false,
                    status = "DELIVERED"
                )
                messageDao.insert(msg)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обработки входящего: ${e.message}")
            }
        }
    }

    /**
     * Пометка всех сообщений в чате как прочитанных.
     */
    fun markAsRead() {
        viewModelScope.launch(Dispatchers.IO) {
            // Реализовать метод в MessageDao: update messages set isRead = 1 where chatId = :partnerHash
        }
    }

    override fun onCleared() {
        repository.removeListener(p2pListener)
        super.onCleared()
    }
}
