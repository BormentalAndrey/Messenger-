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
    
    // CryptoManager теперь object, передаем его экземпляр если нужно или используем напрямую
    private val msgRepo = MessageRepository(CryptoManager) 
    private val dao = ChatDatabase.getDatabase(application).messageDao()

    fun initChat(id: String, uid: String) { // Добавлен uid для соответствия NavGraph
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
                
                viewModelScope.launch {
                    dao.insertAll(processedMsgs)
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

