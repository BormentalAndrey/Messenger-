package com.kakdela.p2p.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore
import com.kakdela.p2p.data.*
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.MessageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var chatId: String = ""

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var listener: ListenerRegistration? = null
    
    // Исправлено создание репозитория (без аргументов)
    private val msgRepo = MessageRepository() 
    private val dao = ChatDatabase.getDatabase(application).messageDao()

    fun initChat(id: String, uid: String) {
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
                
                val processedMsgs = rawMsgs.map { msg ->
                    msg.copy(isMe = msg.senderId == currentUserId)
                }
                _messages.value = processedMsgs
                
                // Сохранение в локальную БД Room
                viewModelScope.launch {
                    val entities = processedMsgs.map { msg ->
                        MessageEntity(
                            chatId = chatId,
                            text = msg.text,
                            senderId = msg.senderId,
                            timestamp = msg.timestamp
                        )
                    }
                    dao.insertAll(entities)
                }
            }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        uploadMessage(Message(text = text, senderId = currentUserId, type = MessageType.TEXT))
    }

    fun sendFile(uri: Uri, type: MessageType) {
        uploadMessage(Message(text = "Файл: ${uri.lastPathSegment}", senderId = currentUserId, type = type))
    }

    fun sendAudio(uri: Uri, duration: Int) {
        uploadMessage(Message(senderId = currentUserId, type = MessageType.AUDIO, durationSeconds = duration))
    }

    fun scheduleMessage(text: String, timeMillis: Long) {
        uploadMessage(Message(text = text, senderId = currentUserId, timestamp = timeMillis))
    }

    private fun uploadMessage(msg: Message) {
        if (chatId.isEmpty()) return
        val ref = db.collection("chats").document(chatId).collection("messages").document()
        val finalMsg = msg.copy(id = ref.id)
        ref.set(finalMsg)
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}

