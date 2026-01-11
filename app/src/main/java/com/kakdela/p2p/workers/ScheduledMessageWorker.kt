package com.kakdela.p2p.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kakdela.p2p.data.MessageRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ScheduledMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val messageRepository: MessageRepository by inject()

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_MESSAGE_ID) 
            ?: return Result.failure().also { Log.e(TAG, "Missing Message ID") }
        
        val chatId = inputData.getString(KEY_CHAT_ID) 
            ?: return Result.failure().also { Log.e(TAG, "Missing Chat ID") }
        
        val text = inputData.getString(KEY_TEXT) 
            ?: return Result.failure().also { Log.e(TAG, "Missing Message Text") }

        return try {
            // Вызываем метод, который мы только что синхронизировали в репозитории
            val isDelivered = messageRepository.performNetworkSend(chatId, messageId, text)

            if (isDelivered) {
                Result.success()
            } else {
                // Если не доставлено (например, пир оффлайн), WorkManager сделает retry
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker execution failed", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "ScheduledWorker"
        private const val MAX_RETRIES = 3

        const val KEY_MESSAGE_ID = "KEY_MESSAGE_ID"
        const val KEY_CHAT_ID = "KEY_CHAT_ID"
        const val KEY_TEXT = "KEY_TEXT"
        const val KEY_TIMESTAMP = "KEY_TIMESTAMP"
    }
}
