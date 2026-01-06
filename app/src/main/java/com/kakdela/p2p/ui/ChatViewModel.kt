package com.kakdela.p2p.ui

import android.app.Application
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
    private val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var chatId: String = ""

    fun initChat(id: String) {
        chatId = id
        listen()
    }

    private fun listen() {
        firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snap, _ ->
                val list = snap?.toObjects(Message::class.java)?.map {
                    it.copy(isMe = it.senderId == uid)
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

    fun send(text: String) {
        if (chatId.isBlank()) return
        val ref = firestore.collection("chats").document(chatId)
            .collection("messages").document()

        ref.set(
            Message(
                id = ref.id,
                senderId = uid,
                text = text,
                type = MessageType.TEXT
            )
        )
    }
}
