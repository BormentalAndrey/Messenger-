package com.kakdela.p2p.ui

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * UI-модель для отображения в списке чатов
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
        // Слушаем все чаты, где участвует текущий пользователь
        db.collection("chats")
            .whereArrayContains("participantIds", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Можно добавить логирование ошибки
                    return@addSnapshotListener
                }

                val list = mutableListOf<ChatDisplay>()

                snapshot?.documents?.forEach { doc ->
                    val lastMessage = doc.getString("lastMessage") ?: ""
                    val timestamp = doc.getTimestamp("timestamp")?.toDate()

                    val time = timestamp?.let {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                    } ?: ""

                    val title = if (doc.id == "global") {
                        "ЧёКаВо?"  // Глобальный чат
                    } else {
                        // Для личных чатов можно загрузить имя собеседника из users
                        "Личный чат"  // Пока заглушка, легко заменить на реальное имя
                    }

                    list.add(
                        ChatDisplay(
                            id = doc.id,
                            title = title,
                            lastMessage = lastMessage,
                            time = time
                        )
                    )
                }

                // Добавляем глобальный чат, если его нет в базе (на всякий случай)
                if (list.none { it.id == "global" }) {
                    list.add(
                        ChatDisplay(
                            id = "global",
                            title = "ЧёКаВо?",
                            lastMessage = "Присоединяйтесь к обсуждению!",
                            time = "online"
                        )
                    )
                }

                // Сортировка по времени последнего сообщения (новые сверху)
                _chats.value = list.sortedByDescending { chat ->
                    if (chat.time == "online") Long.MAX_VALUE else
                        try {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).parse(chat.time)?.time ?: 0
                        } catch (e: Exception) {
                            0
                        }
                }
            }
    }
}
