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

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val p2pListener: (String, String, String, String) -> Unit = { type, data, _, fromId ->
        if (fromId == partnerHash) {
            handleIncomingP2P(type, data, fromId)
        }
    }

    init {
        repository.addListener(p2pListener)
    }

    fun initChat(identifier: String) {
        this.partnerHash = identifier
        viewModelScope.launch(Dispatchers.IO) {
            val node = nodeDao.getNodeByHash(identifier)
            partnerPhone = node?.phone ?: if (identifier.all { it.isDigit() }) identifier else null
            
            messageDao.observeMessages(identifier).collect {
                _messages.value = it
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || partnerHash.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val peerKey = CryptoManager.getPeerPublicKey(partnerHash) ?: ""
                val encryptedText = if (peerKey.isNotEmpty()) CryptoManager.encryptMessage(text, peerKey) else text

                val localMsg = MessageEntity(
                    messageId = UUID.randomUUID().toString(),
                    chatId = partnerHash,
                    senderId = repository.getMyId(),
                    receiverId = partnerHash,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    isMe = true,
                    status = "SENT"
                )
                messageDao.insert(localMsg)

                repository.sendMessageSmart(partnerHash, partnerPhone, encryptedText)
            } catch (e: Exception) {
                Log.e(TAG, "Send error: ${e.message}")
            }
        }
    }

    // Методы для совместимости с NavGraph.kt
    fun sendFile(uri: String, fileName: String) {
        sendMessage("📎 Файл: $fileName")
    }

    fun sendAudio(uri: String, duration: Int) {
        sendMessage("🎤 Голосовое сообщение ($duration сек.)")
    }

    // ФИКС ОШИБКИ Type Mismatch (String вместо Long)
    fun scheduleMessage(text: String, time: String) {
        sendMessage("⏰ [Запланировано на $time]: $text")
    }

    private fun handleIncomingP2P(type: String, encryptedData: String, fromId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val decryptedText = if (type == "CHAT") {
                    CryptoManager.decryptMessage(encryptedData)
                } else {
                    "Media: $type"
                }
                
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
                Log.e(TAG, "Incoming error: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        repository.removeListener(p2pListener)
        super.onCleared()
    }
}
