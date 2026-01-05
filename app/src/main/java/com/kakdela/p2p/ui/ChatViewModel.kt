package com.kakdela.p2p.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.data.Message
import com.kakdela.p2p.data.MessageType
import com.kakdela.p2p.data.StorageService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

/**
 * ViewModel для чата
 * Отправка текстов, файлов, аудио
 * Подписка на изменения сообщений (Firestore + P2P можно интегрировать в дальнейшем)
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Firebase.firestore
    private val currentUserId = Firebase.auth.currentUser?.uid ?: ""
    private var chatId: String = ""

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var listener: ListenerRegistration? = null

    /**
     * Инициализация чата
     */
    fun initChat(chatId: String) {
        this.chatId = chatId
        listenMessages()
    }

    /**
     * Подписка на изменения сообщений в чате
     */
    private fun listenMessages() {
        listener = db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val msgs = snapshot?.toObjects(Message::class.java) ?: emptyList()
                _messages.value = msgs
            }
    }

    /**
     * Отправка текстового сообщения
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        uploadMessage(
            Message(
                text = text,
                senderId = currentUserId,
                type = MessageType.TEXT
            )
        )
    }

    /**
     * Отправка файла или изображения
     */
    fun sendFile(uri: Uri, type: MessageType) {
        viewModelScope.launch {
            try {
                val folder = if (type == MessageType.IMAGE) "images" else "files"
                val url = StorageService.uploadFile(uri, folder)

                uploadMessage(
                    Message(
                        senderId = currentUserId,
                        type = type,
                        fileUrl = url,
                        text = if (type == MessageType.IMAGE) "Фото" else "Файл"
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Отправка голосового сообщения
     */
    fun sendAudio(uri: Uri, duration: Int) {
        viewModelScope.launch {
            try {
                val url = StorageService.uploadFile(uri, "audio")
                uploadMessage(
                    Message(
                        senderId = currentUserId,
                        type = MessageType.AUDIO,
                        fileUrl = url,
                        durationSeconds = duration,
                        text = "Голосовое сообщение"
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Общий метод загрузки сообщения в Firestore и обновления превью чата
     */
    private fun uploadMessage(msg: Message) {
        val ref = db.collection("chats").document(chatId).collection("messages").document()
        val finalMsg = msg.copy(
            id = ref.id,
            timestamp = System.currentTimeMillis()
        )

        // Сохраняем сообщение
        ref.set(finalMsg)

        // Обновляем последнее сообщение чата
        db.collection("chats").document(chatId).update(
            mapOf(
                "lastMessage" to finalMsg,
                "timestamp" to Date()
            )
        )
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
