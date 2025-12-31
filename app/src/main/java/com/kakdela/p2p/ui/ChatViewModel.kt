package com.kakdela.p2p.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.data.WebRtcClient
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.MessageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ChatDatabase.getDatabase(application).messageDao()
    private val db = Firebase.firestore
    
    private var rtcClient: WebRtcClient? = null
    private var currentChatId: String = ""
    private var myUserId: String = ""

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    fun initChat(chatId: String, currentUserId: String) {
        this.currentChatId = chatId
        this.myUserId = currentUserId
        
        // Инициализация WebRTC клиента (для звонков и P2P данных)
        rtcClient = WebRtcClient(getApplication(), chatId, currentUserId)
        
        // Подписка на локальную БД (источник правды для UI)
        viewModelScope.launch {
            dao.getMessagesForChat(chatId).collect { list ->
                // Фильтруем отложенные сообщения (будущее время)
                _messages.value = list.filter { it.timestamp <= System.currentTimeMillis() }
            }
        }
        
        // Подписка на Firestore (резервный канал/офлайн сообщения)
        listenForFirestoreMessages()
    }
    
    // Слушаем облако на предмет сообщений, которые не дошли через P2P
    private fun listenForFirestoreMessages() {
        if (currentChatId.isEmpty()) return
        
        db.collection("chats").document(currentChatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val senderId = data["senderId"] as? String ?: ""
                        
                        // Сохраняем только чужие сообщения (свои мы уже сохранили локально)
                        if (senderId != myUserId) {
                             val text = data["text"] as? String ?: ""
                             val timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()
                             val fileUrl = data["fileUrl"] as? String
                             
                             viewModelScope.launch {
                                 // Простейшая дедупликация: Room с onConflict=REPLACE по ID не сработает,
                                 // так как ID в базе и облаке разные. 
                                 // Но так как UI берет из Room, дублирование возможно только если P2P и Cloud сработали одновременно.
                                 // В данной реализации мы просто сохраняем.
                                 dao.insert(
                                     MessageEntity(
                                         chatId = currentChatId,
                                         text = text,
                                         senderId = senderId,
                                         timestamp = timestamp
                                     )
                                 )
                             }
                        }
                    }
                }
            }
    }

    fun sendMessage(text: String) {
        val timestamp = System.currentTimeMillis()
        
        viewModelScope.launch {
            // 1. Моментально сохраняем сообщение локально (Оптимистичный UI)
            val msgEntity = MessageEntity(
                chatId = currentChatId, 
                text = text, 
                senderId = myUserId, 
                timestamp = timestamp
            )
            dao.insert(msgEntity)

            // 2. Пытаемся отправить через быстрый канал (WebRTC P2P)
            try {
                rtcClient?.sendP2P(text = text, bytes = null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // 3. Дублируем в Firestore (гарантированная доставка)
            // Это обновит lastMessage в списке чатов и доставит сообщение, если P2P упал
            sendToFirestore(text, timestamp)
        }
    }
    
    private fun sendToFirestore(text: String, timestamp: Long) {
        val messageData = mapOf(
            "text" to text,
            "senderId" to myUserId,
            "timestamp" to timestamp
        )
        
        // Добавляем в коллекцию сообщений чата
        db.collection("chats").document(currentChatId)
            .collection("messages")
            .add(messageData)
            
        // Обновляем метаданные самого чата (для списка чатов)
        db.collection("chats").document(currentChatId)
            .update(
                mapOf(
                    "lastMessage" to text,
                    "timestamp" to Date(timestamp)
                )
            )
    }

    fun scheduleMessage(text: String, timeMillis: Long) {
        // Сохраняем локально с будущей меткой времени.
        // UI (initChat) автоматически скроет его, пока время не наступит.
        viewModelScope.launch {
            dao.insert(MessageEntity(
                chatId = currentChatId, 
                text = text, 
                senderId = myUserId, 
                timestamp = timeMillis
            ))
        }
    }

    fun sendFile(bytes: ByteArray) {
        // Для файлов используем пока только P2P (чтобы не забить квоту Firestore)
        viewModelScope.launch {
            rtcClient?.sendP2P(text = "[Файл]", bytes = bytes)
        }
    }
}

