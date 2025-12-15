package com.kakdela.p2p.ui

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * UI-модель для списка чатов
 * НЕ Firestore-модель
 */
data class ChatDisplay(
    val id: String,
    val title: String,
    val lastMessage: String,
    val time: String
)

class ChatsListViewModel : ViewModel() {

    private val db = Firebase.firestore

    private val _chats = MutableStateFlow<List<ChatDisplay>>(emptyList())
    val chats = _chats.asStateFlow()

    fun loadChats(currentUserId: String) {
        db.collection("chats")
            .whereArrayContains("participantIds", currentUserId)
            .addSnapshotListener { snapshot, _ ->

                val list = mutableListOf<ChatDisplay>()

                snapshot?.documents?.forEach { doc ->
                    val lastMessage = doc.getString("lastMessage") ?: ""
                    val timestamp = doc.getTimestamp("timestamp")?.toDate()

                    val time = timestamp?.let {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                    } ?: ""

                    list.add(
                        ChatDisplay(
                            id = doc.id,
                            title = "Личный чат",
                            lastMessage = lastMessage,
                            time = time
                        )
                    )
                }

                // Глобальный чат всегда есть
                if (list.none { it.id == "global" }) {
                    list.add(
                        ChatDisplay(
                            id = "global",
                            title = "Глобальный чат",
                            lastMessage = "Присоединяйтесь!",
                            time = "online"
                        )
                    )
                }

                _chats.value = list
            }
    }
}
