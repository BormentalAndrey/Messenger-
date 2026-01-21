package com.kakdela.p2p.ui

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.MessageRepository
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ChatViewModel: Управляет UI-состоянием чата.
 * Реализует ГИБРИДНОЕ отображение: P2P сообщения из локальной БД + Системные SMS.
 */
class ChatViewModel(
    application: Application,
    private val identityRepo: IdentityRepository,
    private val messageRepo: MessageRepository
) : AndroidViewModel(application) {

    private val TAG = "ChatViewModel"
    private val context = application.applicationContext
    private val db = ChatDatabase.getDatabase(application)
    private val messageDao = db.messageDao()
    private val nodeDao = db.nodeDao()

    private var partnerHash: String = ""
    private var partnerPhone: String? = null

    // Единый поток сообщений (P2P + SMS)
    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    /**
     * Инициализация чата.
     * identifier: может быть P2P Hash (UUID) или номером телефона.
     */
    fun initChat(identifier: String) {
        partnerHash = identifier
        
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Пытаемся определить номер телефона
            partnerPhone = resolvePhoneNumber(identifier)

            // 2. Создаем Flow для локальной БД (P2P история + сохраненные нами SMS)
            val dbFlow = messageDao.observeMessages(identifier)

            // 3. Создаем Flow для системных SMS (если есть номер телефона)
            val smsFlow = if (!partnerPhone.isNullOrBlank()) {
                observeSystemSms(partnerPhone!!)
            } else {
                flowOf(emptyList())
            }

            // 4. Объединяем потоки
            combine(dbFlow, smsFlow) { dbMsgs, smsMsgs ->
                mergeMessages(dbMsgs, smsMsgs)
            }
            .distinctUntilChanged()
            .collect { list ->
                _messages.value = list
            }
        }
    }

    /**
     * Определяет номер телефона по идентификатору.
     * Если identifier похож на телефон - возвращает его.
     * Если это Hash - ищет в базе нод.
     */
    private suspend fun resolvePhoneNumber(identifier: String): String? {
        if (identifier.matches(Regex("^[+]?[0-9 -]{10,15}$"))) {
            return identifier.replace(Regex("[^0-9+]"), "")
        }
        return try {
            val node = nodeDao.getNodeByHash(identifier)
            node?.phone?.replace(Regex("[^0-9+]"), "")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Считывает системные SMS через ContentProvider и подписывается на изменения.
     */
    private fun observeSystemSms(phoneNumber: String): Flow<List<MessageEntity>> = callbackFlow {
        val uri = Telephony.Sms.CONTENT_URI
        
        // Функция чтения SMS
        fun load(): List<MessageEntity> {
            val list = mutableListOf<MessageEntity>()
            try {
                // Ищем SMS по адресу (номеру). Нормализация номера сложная тема,
                // здесь используем упрощенный поиск 'like' для надежности
                val selection = "${Telephony.Sms.ADDRESS} LIKE ?"
                val selectionArgs = arrayOf("%${phoneNumber.takeLast(10)}%") 
                
                context.contentResolver.query(
                    uri,
                    arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                    selection,
                    selectionArgs,
                    "${Telephony.Sms.DATE} DESC"
                )?.use { cursor ->
                    val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                    val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE) // 1 = Inbox, 2 = Sent

                    while (cursor.moveToNext()) {
                        val type = cursor.getInt(typeIdx)
                        val isMe = type == Telephony.Sms.MESSAGE_TYPE_SENT
                        
                        list.add(MessageEntity(
                            messageId = "sms_${cursor.getLong(dateIdx)}", // Генерируем ID
                            chatId = partnerHash,
                            senderId = if (isMe) identityRepo.getMyId() else partnerHash,
                            receiverId = if (isMe) partnerHash else identityRepo.getMyId(),
                            text = cursor.getString(bodyIdx) ?: "",
                            timestamp = cursor.getLong(dateIdx),
                            isMe = isMe,
                            status = if (isMe) "SENT_SMS" else "RECEIVED_SMS",
                            messageType = "TEXT"
                        ))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading system SMS", e)
            }
            return list
        }

        // Отправляем начальные данные
        trySend(load())

        // Регистрируем наблюдателя за изменениями в БД SMS
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(load())
            }
        }
        try {
            context.contentResolver.registerContentObserver(uri, true, observer)
        } catch (e: SecurityException) {
            // Нет прав
        }

        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Объединяет сообщения из локальной БД и системные SMS.
     * Убирает дубликаты (если мы сохранили отправленное SMS в БД, оно есть и в системе).
     */
    private fun mergeMessages(local: List<MessageEntity>, system: List<MessageEntity>): List<MessageEntity> {
        val merged = ArrayList<MessageEntity>()
        merged.addAll(local)
        
        // Добавляем системные SMS, если похожих нет в локальной базе
        // Критерий похожести: время (в пределах 2 сек) и текст
        for (sysMsg in system) {
            val isDuplicate = local.any { locMsg ->
                val timeDiff = kotlin.math.abs(locMsg.timestamp - sysMsg.timestamp)
                timeDiff < 2000 && locMsg.text == sysMsg.text
            }
            if (!isDuplicate) {
                merged.add(sysMsg)
            }
        }
        
        return merged.sortedBy { it.timestamp }
    }

    /* === Методы отправки (без изменений логики) === */

    fun sendMessage(text: String) {
        if (text.isBlank() || partnerHash.isBlank()) return
        messageRepo.sendText(partnerHash, text)
    }

    fun sendFile(uri: Uri, fileName: String) {
        if (partnerHash.isBlank()) return
        val type = when {
            fileName.endsWith(".jpg", ignoreCase = true) ||
            fileName.endsWith(".jpeg", ignoreCase = true) ||
            fileName.endsWith(".png", ignoreCase = true) -> "image"
            fileName.endsWith(".mp3", ignoreCase = true) ||
            fileName.endsWith(".wav", ignoreCase = true) ||
            fileName.endsWith(".m4a", ignoreCase = true) -> "audio"
            else -> "file"
        }
        messageRepo.sendFile(partnerHash, uri, type, fileName)
    }

    fun sendAudioMessage(uri: Uri, fileName: String) {
        if (partnerHash.isBlank()) return
        messageRepo.sendFile(partnerHash, uri, "audio", fileName)
    }

    fun scheduleMessage(text: String, timestamp: Long) {
        if (text.isBlank() || partnerHash.isBlank()) return
        messageRepo.sendText(partnerHash, text, scheduledTime = timestamp)
    }
}
