package com.kakdela.p2p.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.kakdela.p2p.data.Message
import com.kakdela.p2p.data.MessageType
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ScheduledMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "ScheduledMessageWorker"

        const val KEY_MESSAGE_ID = "message_id"
        const val KEY_CHAT_ID = "chat_id"
        const val KEY_TEXT = "text"
        const val KEY_TIMESTAMP = "timestamp"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val messageId = inputData.getString(KEY_MESSAGE_ID) ?: return@withContext Result.failure()
            val chatId = inputData.getString(KEY_CHAT_ID) ?: return@withContext Result.failure()
            val text = inputData.getString(KEY_TEXT) ?: ""
            val timestamp = inputData.getLong(KEY_TIMESTAMP, System.currentTimeMillis())

            val db = ChatDatabase.getDatabase(applicationContext)
            val messageDao = db.messageDao()
            val identityRepository = IdentityRepository(applicationContext)
            val messageRepository = MessageRepository(
                applicationContext,
                messageDao,
                identityRepository
            )

            val message = Message(
                id = messageId,
                text = text,
                senderId = identityRepository.getMyId(),
                timestamp = timestamp,
                type = MessageType.TEXT,
                isMe = true,
                scheduledTime = null, // ВАЖНО: уже не запланированное
                status = "SENT"
            )

            // 1. Сохраняем в БД как обычное сообщение
            messageRepository.insertOutgoing(message)

            // 2. Реально отправляем
            val delivered = messageRepository.sendMessageNow(chatId, message)

            if (!delivered) {
                messageRepository.updateStatus(message.id, "FAILED")
            }

            Log.i(TAG, "Scheduled message sent: ${message.id}")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Worker failed: ${e.message}", e)
            Result.retry()
        }
    }
}
