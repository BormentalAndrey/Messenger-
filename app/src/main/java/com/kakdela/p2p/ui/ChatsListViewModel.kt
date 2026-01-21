package com.kakdela.p2p.ui

import android.app.Application
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.data.local.ChatDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Модель отображения чата.
 * isSms - флаг, указывающий, что чат загружен из системной базы SMS.
 */
data class ChatDisplay(
    val id: String,
    val title: String,
    val lastMessage: String,
    val time: String,
    val timestamp: Long,
    val isSms: Boolean = false
)

class ChatsListViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ChatDatabase.getDatabase(application)
    private val messageDao = db.messageDao()
    private val nodeDao = db.nodeDao()

    private val _dbChats = MutableStateFlow<List<ChatDisplay>>(emptyList())
    private val _smsChats = MutableStateFlow<List<ChatDisplay>>(emptyList())

    // Объединяем локальные P2P чаты и системные SMS
    val chats: StateFlow<List<ChatDisplay>> = combine(_dbChats, _smsChats) { dbList, smsList ->
        (dbList + smsList)
            .distinctBy { it.id } // Простейшая дедупликация (можно улучшить по нормализации телефона)
            .sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("dd.MM", Locale.getDefault())

    init {
        observeDbChats()
        refreshSms()
    }

    /**
     * Загрузка системных SMS чатов (сразу, без ожидания синхронизации)
     */
    fun refreshSms() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val smsList = loadSystemSms()
                _smsChats.value = smsList
            } catch (e: Exception) {
                // Скорее всего нет разрешения READ_SMS
                _smsChats.value = emptyList()
            }
        }
    }

    private fun observeDbChats() {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.observeLastMessages()
                .distinctUntilChanged()
                .collect { messages ->
                    val displayList = messages
                        .filter { it.chatId != "global" }
                        .map { msg ->
                            val contact = try {
                                nodeDao.getNodeByHash(msg.chatId)
                            } catch (e: Exception) { null }

                            val title = when {
                                contact != null && !contact.phone.isNullOrBlank() -> contact.phone
                                else -> msg.chatId // Если нет контакта, показываем ID (или номер телефона если это был SMS-чат)
                            }

                            ChatDisplay(
                                id = msg.chatId,
                                title = title ?: "Неизвестный",
                                lastMessage = if (msg.isMe) "Вы: ${msg.text}" else msg.text,
                                time = formatTimestamp(msg.timestamp),
                                timestamp = msg.timestamp,
                                isSms = false
                            )
                        }
                    _dbChats.value = displayList
                }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun loadSystemSms(): List<ChatDisplay> = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val result = mutableListOf<ChatDisplay>()
        
        // Используем contentResolver для чтения SMS
        // Группируем по адресу, берем последнее
        // Для простоты читаем входящие (Inbox) и исходящие (Sent) и группируем вручную или используем content://mms-sms/conversations
        // Здесь используем упрощенный запрос к Telephony.Sms для получения последних сообщений
        
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        // Сортируем по дате DESC
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        try {
            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                
                val processedNumbers = HashSet<String>()

                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIdx)
                    if (address.isNullOrBlank()) continue
                    
                    // Если уже добавили чат с этим номером, пропускаем (т.к. сортировка DESC, первый - самый свежий)
                    if (processedNumbers.contains(address)) continue
                    
                    processedNumbers.add(address)

                    val body = cursor.getString(bodyIdx)
                    val date = cursor.getLong(dateIdx)
                    
                    result.add(ChatDisplay(
                        id = address, // Для SMS ID - это адрес отправителя
                        title = address,
                        lastMessage = body ?: "",
                        time = formatTimestamp(date),
                        timestamp = date,
                        isSms = true
                    ))
                }
            }
        } catch (e: SecurityException) {
            // Разрешения не даны
            return@withContext emptyList()
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        
        return@withContext result
    }

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
