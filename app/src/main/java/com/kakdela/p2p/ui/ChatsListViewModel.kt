package com.kakdela.p2p.ui

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.data.Chat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatsListViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats = _chats.asStateFlow()

    fun loadChats(currentUserId: String) {
        // Пример: слушаем все чаты, где участвует пользователь
        db.collection("chats")
            .whereArrayContains("participantIds", currentUserId)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(Chat::class.java) ?: emptyList()
                _chats.value = list.sortedByDescending { it.timestamp }
            }

        // Добавьте глобальный чат вручную, если нужно
        if (_chats.value.none { it.id == "global" }) {
            // Можно добавить заглушку
        }
    }
}
