package com.kakdela.p2p.ui

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

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
                    val participantIds = doc.get("participantIds") as? List<String> ?: emptyList()
                    val title = if (doc.id == "global") "Глобальный чат" else "Личный чат"
                    val lastMessage = doc.getString("lastMessage") ?: ""
                    val timestamp = doc.getTimestamp("timestamp")?.toDate()
                    val time = timestamp?.let {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                    } ?: ""

                    list.add(ChatDisplay(doc.id, title, lastMessage, time))
                }

                // Глобальный чат всегда в списке
                if (list.none { it.id == "global" }) {
                    list.add(ChatDisplay("global", "Глобальный чат", "Присоединяйтесь!", "Онлайн"))
                }

                _chats.value = list.sortedByDescending { 
                    // Сортировка по времени (глобальный внизу)
                    if (it.id == "global") 0 else it.time.takeIf { it != "" }?.let { 
                        try { SimpleDateFormat("HH:mm", Locale.getDefault()).parse(it)?.time ?: 0 } catch (e: Exception) { 0 }
                    } ?: 0
                }
            }
    }
}
