package com.kakdela.p2p.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kakdela.p2p.data.MessageRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Воркер для отправки отложенных сообщений.
 * Вызывается WorkManager-ом в заданное пользователем время.
 * Реализует интерфейс KoinComponent для получения доступа к синглтону MessageRepository.
 */
class ScheduledMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    // Инъекция репозитория через Koin
    private val messageRepository: MessageRepository by inject()

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_MESSAGE_ID) 
            ?: return Result.failure().also { Log.e(TAG, "Missing Message ID") }
        
        val chatId = inputData.getString(KEY_CHAT_ID) 
            ?: return Result.failure().also { Log.e(TAG, "Missing Chat ID") }
        
        val text = inputData.getString(KEY_TEXT) 
            ?: return Result.failure().also { Log.e(TAG, "Missing Message Text") }

        Log.d(TAG, "Starting scheduled send task for message: $messageId")

        return try {
            /**
             * Вызываем метод сетевой отправки из репозитория.
             * Внутри performNetworkSend происходит шифрование E2EE и передача в P2P сеть.
             */
            val isDelivered = messageRepository.performNetworkSend(chatId, messageId, text)

            if (isDelivered) {
                Log.i(TAG, "Scheduled message $messageId successfully sent.")
                Result.success()
            } else {
                // Если сеть недоступна, WorkManager перезапустит задачу позже согласно политике бэкоффа
                Log.w(TAG, "Delivery failed for $messageId, retrying...")
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in ScheduledMessageWorker for $messageId: ${e.message}")
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "ScheduledWorker"
        private const val MAX_RETRIES = 3

        // Константы для ключей Input Data (должны совпадать с функцией scheduleMessageWork)
        const val KEY_MESSAGE_ID = "KEY_MESSAGE_ID"
        const val KEY_CHAT_ID = "KEY_CHAT_ID"
        const val KEY_TEXT = "KEY_TEXT"
        const val KEY_TIMESTAMP = "KEY_TIMESTAMP"
    }
}
