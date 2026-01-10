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

    private val db = ChatDatabase.getDatabase(application)
    private val messageDao = db.messageDao()
    private val nodeDao = db.nodeDao()

    private var partnerHash: String = ""
    private var partnerPhone: String? = null

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val p2pListener: (String, String, String, String) -> Unit = { type, data, _, fromId ->
        if (fromId == partnerHash) {
            handleIncoming(type, data, fromId)
        }
    }

    init {
        repository.addListener(p2pListener)
    }

    fun initChat(identifier: String) {
        partnerHash = identifier
        viewModelScope.launch(Dispatchers.IO) {
            val node = nodeDao.getNodeByHash(identifier)
            partnerPhone = node?.phone
            messageDao.observeMessages(identifier).collect { _messages.value = it }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || partnerHash.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val peerKey = CryptoManager.getPeerPublicKey(partnerHash) ?: ""
                val encrypted = if (peerKey.isNotEmpty()) CryptoManager.encryptMessage(text, peerKey) else text
                
                // FIX: getMyId() теперь доступен
                val myId = repository.getMyId()
                
                val msg = MessageEntity(
                    messageId = UUID.randomUUID().toString(),
                    chatId = partnerHash,
                    senderId = myId,
                    receiverId = partnerHash,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    isMe = true,
                    status = "SENT"
                )
                messageDao.insert(msg)
                repository.sendMessageSmart(partnerHash, partnerPhone, encrypted)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun sendFile(uri: String, fileName: String) = sendMessage("FILE: $fileName")
    fun sendAudio(uri: String, duration: Int) = sendMessage("AUDIO: $duration s")
    fun scheduleMessage(text: String, time: String) = sendMessage("SCHEDULED ($time): $text")

    private fun handleIncoming(type: String, data: String, fromId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = if (type == "CHAT") CryptoManager.decryptMessage(data) else "$type received"
                val myId = repository.getMyId()
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
                messageDao.insert(msg)
            } catch (e: Exception) {}
        }
    }

    override fun onCleared() {
        repository.removeListener(p2pListener)
        super.onCleared()
    }
}
