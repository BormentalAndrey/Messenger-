package com.kakdela.p2p.ui

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.data.ChatDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
                    val participantIds = doc.get("participantIds") as? List<String> ?: emptyList()
                    val title = if (doc.id == "global") "Глобальный чат" else "Личный чат"
                    val lastMessage = doc.getString("lastMessage") ?: ""
                    val timestamp = doc.getTimestamp("timestamp")?.toDate()
                    val time = timestamp?.let {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                    } ?: ""

                    list.add(ChatDisplay(doc.id, title, lastMessage, time))
                }

                // Добавляем глобальный чат, если его нет
                if (list.none { it.id == "global" }) {
                    list.add(ChatDisplay("global", "Глобальный чат", "Присоединяйтесь!", "Онлайн"))
                }

                _chats.value = list.sortedByDescending { it.time }
            }
    }
}
