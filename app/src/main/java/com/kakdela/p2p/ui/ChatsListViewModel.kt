package com.kakdela.p2p.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.data.local.ChatDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Модель отображения чата в списке.
 * Содержит всё необходимое для отрисовки элемента списка.
 */
data class ChatDisplay(
    val id: String,
    val title: String,
    val lastMessage: String,
    val time: String,
    val timestamp: Long
)

class ChatsListViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ChatDatabase.getDatabase(application)
    private val messageDao = db.messageDao()
    private val nodeDao = db.nodeDao()

    private val _chats = MutableStateFlow<List<ChatDisplay>>(emptyList())
    val chats: StateFlow<List<ChatDisplay>> = _chats.asStateFlow()

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("dd.MM", Locale.getDefault())

    init {
        observeChats()
    }

    /**
     * Подписывается на поток последних сообщений из БД.
     * Автоматически срабатывает при insert/update в таблице messages.
     */
    private fun observeChats() {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.observeLastMessages()
                .distinctUntilChanged()
                .collect { messages ->
                    val displayList = messages
                        .filter { it.chatId != "global" } // Исключаем глобальный чат
                        .map { msg ->
                            // Получаем данные о пире для заголовка
                            val contact = try {
                                nodeDao.getNodeByHash(msg.chatId)
                            } catch (e: Exception) {
                                null
                            }

                            val title = when {
                                contact != null && !contact.phone.isNullOrBlank() -> contact.phone
                                // Если в NodeEntity есть поле name (согласно ТЗ):
                                // contact != null && !contact.name.isNullOrBlank() -> contact.name
                                else -> "ID: ${msg.chatId.take(8)}..."
                            }

                            ChatDisplay(
                                id = msg.chatId,
                                title = title ?: "Unknown",
                                lastMessage = if (msg.isMe) "Вы: ${msg.text}" else msg.text,
                                time = formatTimestamp(msg.timestamp),
                                timestamp = msg.timestamp
                            )
                        }

                    // Обновляем состояние (сортировка уже заложена в SQL-запросе, но дублируем здесь для надежности)
                    _chats.value = displayList.sortedByDescending { it.timestamp }
                }
        }
    }

    /**
     * Форматирует время: если сообщение сегодня — HH:mm, если раньше — dd.MM
     */
    private fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0) return ""

        val now = Calendar.getInstance()
        val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }

        return if (now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR)) {
            timeFormatter.format(Date(timestamp))
        } else {
            dateFormatter.format(Date(timestamp))
        }
    }
}
