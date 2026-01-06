package com.kakdela.p2p.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kakdela.p2p.data.Message
import com.kakdela.p2p.data.MessageType
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.MessageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val firestore = FirebaseFirestore.getInstance()
    private val dao = ChatDatabase.getDatabase(app).messageDao()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var chatId: String = ""

    fun initChat(id: String) {
        chatId = id
        listenMessages()
    }

    // 🔹 Совместимость с NavGraph
    fun initChat(id: String, uid: String) {
        chatId = id
        listenMessages()
    }

    private fun listenMessages() {
        if (chatId.isBlank()) return

        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snap, _ ->
                val list = snap?.toObjects(Message::class.java)?.map {
                    it.copy(isMe = it.senderId == currentUserId)
                } ?: emptyList()

                _messages.value = list

                viewModelScope.launch {
                    dao.insertAll(
                        list.map {
                            MessageEntity(
                                id = it.id,
                                chatId = chatId,
                                senderId = it.senderId,
                                text = it.text,
                                timestamp = it.timestamp
                            )
                        }
                    )
                }
            }
    }

    fun sendMessage(text: String) {
        if (chatId.isBlank() || text.isBlank()) return

        val ref = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document()

        ref.set(
            Message(
                id = ref.id,
                senderId = currentUserId,
                text = text,
                type = MessageType.TEXT
            )
        )
    }

    fun sendFile(uri: Uri, type: MessageType) {
        sendMessage("Файл: ${uri.lastPathSegment}")
    }

    fun sendAudio(uri: Uri, duration: Int) {
        sendMessage("Аудио ($duration сек)")
    }

    fun scheduleMessage(text: String, timeMillis: Long) {
        val ref = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document()

        ref.set(
            Message(
                id = ref.id,
                senderId = currentUserId,
                text = text,
                timestamp = timeMillis,
                type = MessageType.TEXT
            )
        )
    }
}
