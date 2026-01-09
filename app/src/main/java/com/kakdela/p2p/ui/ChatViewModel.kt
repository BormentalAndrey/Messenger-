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

    lateinit var messages: StateFlow<List<MessageEntity>>

    private val p2pListener: (String, String, String, String) -> Unit = { type, data, _, fromId ->
        if (fromId == partnerHash) {
            handleIncomingP2P(type, data, fromId)
        }
    }

    init {
        repository.addListener(p2pListener)
    }

    fun initChat(partnerHash: String) {
        this.partnerHash = partnerHash
        viewModelScope.launch(Dispatchers.IO) {
            val node = nodeDao.getNodeByHash(partnerHash)
            partnerPhone = node?.phone
        }
        messages = messageDao.observeMessages(partnerHash)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || partnerHash.isBlank()) return

        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Шифрование
                val peerKey = CryptoManager.getPeerPublicKey(partnerHash) ?: ""
                val encryptedText = if (peerKey.isNotEmpty()) CryptoManager.encryptMessage(text, peerKey) else text

                val localMsg = MessageEntity(
                    messageId = messageId,
                    chatId = partnerHash,
                    senderId = repository.getMyId(),
                    receiverId = partnerHash,
                    text = text,
                    timestamp = timestamp,
                    isMe = true,
                    status = "PENDING",
                    messageType = "TEXT"
                )
                messageDao.insert(localMsg)

                repository.sendMessageSmart(partnerHash, partnerPhone, encryptedText).join()
                messageDao.updateStatus(messageId, "SENT")

            } catch (e: Exception) {
                Log.e(TAG, "Error sending: ${e.message}")
                messageDao.updateStatus(messageId, "ERROR")
            }
        }
    }

    // --- Методы для NavGraph (исправлено) ---

    fun sendFile(uri: String, fileName: String) {
        // В реальном проекте здесь запуск FileTransferWorker
        sendMessage("📎 Файл: $fileName")
    }

    fun sendAudio(uri: String, duration: Int) {
        sendMessage("🎤 Аудио: $duration сек.")
    }

    fun scheduleMessage(text: String, time: String) {
        sendMessage("⏰ Отложено на $time: $text")
    }

    // ----------------------------------------

    private fun handleIncomingP2P(type: String, encryptedData: String, fromId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val decryptedText = if (type == "CHAT") CryptoManager.decryptMessage(encryptedData) else "Media: $type"
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
            } catch (e: Exception) { Log.e(TAG, "Handle incoming error: ${e.message}") }
        }
    }

    override fun onCleared() {
        repository.removeListener(p2pListener)
        super.onCleared()
    }
}
