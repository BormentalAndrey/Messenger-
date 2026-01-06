package com.kakdela.p2p.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.data.*
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.security.CryptoManager
import com.kakdela.p2p.data.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Firebase.firestore
    private val currentUserId = Firebase.auth.currentUser?.uid ?: ""
    private var chatId: String = ""

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var listener: ListenerRegistration? = null
    
    // Инициализация компонентов (исправленные пути)
    private val msgRepo = MessageRepository(CryptoManager) 
    private val dao = ChatDatabase.getDatabase(application).messageDao()

    fun initChat(id: String) {
        this.chatId = id
        listenMessages()
    }

    private fun listenMessages() {
        if (chatId.isEmpty()) return
        listener?.remove()
        listener = db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                val rawMsgs = snapshot?.toObjects(Message::class.java) ?: emptyList()
                
                // Расшифровка сообщений перед показом в UI
                val processedMsgs = rawMsgs.map { msg ->
                    if (msg.type == MessageType.TEXT) {
                        msg.copy(text = msgRepo.decryptMessage(msg.text), isMe = msg.senderId == currentUserId)
                    } else {
                        msg.copy(isMe = msg.senderId == currentUserId)
                    }
                }
                _messages.value = processedMsgs
                
                // Сохранение в локальную зашифрованную БД Room
                viewModelScope.launch {
                    dao.insertAll(processedMsgs)
                }
            }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        // Шифруем текст перед отправкой в Firebase
        val encryptedText = msgRepo.encryptMessage(text)
        uploadMessage(Message(text = encryptedText, senderId = currentUserId, type = MessageType.TEXT))
    }

    private fun uploadMessage(msg: Message) {
        if (chatId.isEmpty()) return
        val ref = db.collection("chats").document(chatId).collection("messages").document()
        val finalMsg = msg.copy(
            id = ref.id, 
            timestamp = if(msg.timestamp == 0L) System.currentTimeMillis() else msg.timestamp
        )
        ref.set(finalMsg)
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}

