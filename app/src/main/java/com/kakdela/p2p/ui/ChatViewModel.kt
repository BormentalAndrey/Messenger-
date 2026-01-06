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
import com.kakdela.p2p.data.local.MessageEntity
import com.kakdela.p2p.security.CryptoManager
import com.kakdela.p2p.data.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Firebase.firestore
    private val currentUserId = Firebase.auth.currentUser?.uid ?: ""
    private var chatId: String = ""

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var listener: ListenerRegistration? = null
    private val dao = ChatDatabase.getDatabase(application).messageDao()
    
    // Инициализация будет зависеть от ваших путей к файлам
    private val crypto = CryptoManager(application)
    private val msgRepo = MessageRepository(crypto)

    fun initChat(chatId: String, uid: String) {
        this.chatId = chatId
        listenMessages()
    }

    private fun listenMessages() {
        listener?.remove()
        listener = db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val msgs = snapshot?.toObjects(Message::class.java) ?: emptyList()
                _messages.value = msgs
            }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        uploadMessage(Message(text = text, senderId = currentUserId, type = MessageType.TEXT))
    }

    fun scheduleMessage(text: String, timeMillis: Long) {
        // Логика планирования через WorkManager
        uploadMessage(Message(text = "[Запланировано]: $text", senderId = currentUserId, timestamp = timeMillis))
    }

    fun sendFile(uri: Uri, type: MessageType) {
        viewModelScope.launch {
            // Временная заглушка, так как StorageService должен быть реализован вами
            uploadMessage(Message(senderId = currentUserId, type = type, text = "Файл отправлен"))
        }
    }

    fun sendAudio(uri: Uri, duration: Int) {
        uploadMessage(Message(senderId = currentUserId, type = MessageType.AUDIO, durationSeconds = duration))
    }

    private fun uploadMessage(msg: Message) {
        val ref = db.collection("chats").document(chatId).collection("messages").document()
        val finalMsg = msg.copy(id = ref.id, timestamp = if(msg.timestamp == 0L) System.currentTimeMillis() else msg.timestamp)
        ref.set(finalMsg)
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}

