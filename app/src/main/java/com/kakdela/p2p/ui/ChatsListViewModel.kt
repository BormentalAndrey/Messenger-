package com.kakdela.p2p.ui

import android.app.Application
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
 *
 * id              — основной идентификатор (hash P2P или телефон)
 * lastMessageIsSms — тип последнего сообщения (для иконки)
 * p2pHash         — hash P2P-чата (если есть)
 * phoneNumber     — номер телефона (если есть)
 */
data class ChatDisplay(
    val id: String,
    val title: String,
    val lastMessage: String,
    val time: String,
    val timestamp: Long,
    val lastMessageIsSms: Boolean,
    val p2pHash: String? = null,
    val phoneNumber: String? = null
)

class ChatsListViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ChatDatabase.getDatabase(application)
    private val messageDao = db.messageDao()
    private val nodeDao = db.nodeDao()

    private val _dbChats = MutableStateFlow<List<ChatDisplay>>(emptyList())
    private val _smsChats = MutableStateFlow<List<ChatDisplay>>(emptyList())

    /**
     * Итоговый список чатов:
     * - объединяет SMS и P2P
     * - группирует по телефону (если есть)
     * - выбирает самое свежее сообщение
     */
    val chats: StateFlow<List<ChatDisplay>> =
        combine(_dbChats, _smsChats) { dbList, smsList ->
            val all = dbList + smsList

            all.groupBy { chat ->
                val phone = chat.phoneNumber
                    ?: if (isValidPhoneNumber(chat.id)) chat.id else null

                phone?.let { normalizePhoneNumber(it) } ?: chat.id
            }.map { (_, items) ->
                val latest = items.maxByOrNull { it.timestamp }!!

                val p2pHash = items.firstOrNull { it.p2pHash != null }?.p2pHash
                val phoneNumber =
                    items.firstOrNull { it.phoneNumber != null }?.phoneNumber
                        ?: if (isValidPhoneNumber(latest.id)) latest.id else null

                latest.copy(
                    p2pHash = p2pHash,
                    phoneNumber = phoneNumber,
                    title = phoneNumber ?: latest.title
                )
            }.sortedByDescending { it.timestamp }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("dd.MM", Locale.getDefault())

    init {
        observeDbChats()
        refreshSms()
    }

    fun refreshSms() {
        viewModelScope.launch(Dispatchers.IO) {
            _smsChats.value = loadSystemSms()
        }
    }

    /**
     * Наблюдение за P2P чатами из Room
     */
    private fun observeDbChats() {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.observeLastMessages()
                .distinctUntilChanged()
                .collect { messages ->
                    val chats = messages
                        .filter { it.chatId != "global" }
                        .map { msg ->
                            val node = runCatching {
                                nodeDao.getNodeByHash(msg.chatId)
                            }.getOrNull()

                            val phone = node?.phone

                            ChatDisplay(
                                id = msg.chatId,
                                title = phone ?: msg.chatId,
                                lastMessage = if (msg.isMe) "Вы: ${msg.text}" else msg.text,
                                time = formatTimestamp(msg.timestamp),
                                timestamp = msg.timestamp,
                                lastMessageIsSms = false,
                                p2pHash = msg.chatId,
                                phoneNumber = phone
                            )
                        }

                    _dbChats.value = chats
                }
        }
    }

    /**
     * Загрузка SMS из системной БД
     * Один чат = один номер (берём самое свежее сообщение)
     */
    private suspend fun loadSystemSms(): List<ChatDisplay> = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val result = mutableListOf<ChatDisplay>()
        val processed = HashSet<String>()

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val sortOrder = "${Telephony.Sms.DATE} DESC"

        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIdx) ?: continue
                    val normalized = normalizePhoneNumber(address)

                    if (!processed.add(normalized)) continue

                    val body = cursor.getString(bodyIdx)
                    val date = cursor.getLong(dateIdx)

                    result.add(
                        ChatDisplay(
                            id = address,
                            title = address,
                            lastMessage = body.orEmpty(),
                            time = formatTimestamp(date),
                            timestamp = date,
                            lastMessageIsSms = true,
                            phoneNumber = address
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        result
    }

    private fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0) return ""

        val now = Calendar.getInstance()
        val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }

        return if (
            now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR)
        ) {
            timeFormatter.format(Date(timestamp))
        } else {
            dateFormatter.format(Date(timestamp))
        }
    }

    /**
     * Нормализация номера:
     * - только цифры
     * - 8XXXXXXXXXX → 7XXXXXXXXXX
     */
    private fun normalizePhoneNumber(phone: String): String {
        val digits = phone.replace(Regex("[^0-9]"), "")
        return if (digits.length == 11 && digits.startsWith("8")) {
            "7${digits.substring(1)}"
        } else {
            digits
        }
    }

    private fun isValidPhoneNumber(input: String): Boolean {
        val digits = input.replace(Regex("[^0-9]"), "")
        return digits.length >= 5
    }
}
