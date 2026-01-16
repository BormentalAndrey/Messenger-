package com.kakdela.p2p.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.MessageRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ScheduledMessageWorker: Воркер для выполнения отложенной отправки сообщений.
 * Работает в фоновом режиме через WorkManager, гарантируя доставку даже после перезагрузки.
 * Реализует логику повторов (Backoff) при отсутствии сети.
 */
class ScheduledMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    // Инъекция репозиториев через Koin (Production-ready DI)
    private val messageRepository: MessageRepository by inject()
    private val identityRepository: IdentityRepository by inject()

    override suspend fun doWork(): Result {
        // 1. Извлечение данных из InputData (синхронизировано с MessageRepository.sendText)
        val messageId = inputData.getString(KEY_MESSAGE_ID) 
            ?: return Result.failure().also { Log.e(TAG, "Ошибка: Отсутствует ID сообщения") }
        
        val chatId = inputData.getString(KEY_CHAT_ID) 
            ?: return Result.failure().also { Log.e(TAG, "Ошибка: Отсутствует ID чата") }
        
        val text = inputData.getString(KEY_TEXT) 
            ?: return Result.failure().also { Log.e(TAG, "Ошибка: Отсутствует текст сообщения") }

        Log.d(TAG, "Запуск отложенной отправки для сообщения: $messageId (Попытка: $runAttemptCount)")

        return try {
            // 2. Получаем актуальные данные о пире непосредственно перед отправкой.
            // За время ожидания (часы или дни) IP-адрес или номер телефона могли обновиться.
            val peerNode = identityRepository.getCachedNode(chatId)
            val receiverPhone = peerNode?.phone

            // 3. Выполнение сетевой трансляции через MessageRepository.
            // Метод performNetworkSend обеспечит E2EE шифрование и выбор UDP или SMS.
            val isDelivered = messageRepository.performNetworkSend(
                chatId = chatId,
                messageId = messageId,
                payload = text,
                phone = receiverPhone
            )

            if (isDelivered) {
                Log.i(TAG, "Отложенное сообщение $messageId успешно отправлено в сеть")
                Result.success()
            } else {
                // Если доставка не удалась (нет сети или пир оффлайн)
                if (runAttemptCount < MAX_RETRIES) {
                    Log.w(TAG, "Доставка не удалась. Назначаем повтор через экспоненциальную задержку.")
                    Result.retry()
                } else {
                    Log.e(TAG, "Достигнут лимит попыток для сообщения $messageId. Отмена.")
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка выполнения WorkManager: ${e.message}", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "ScheduledWorker"
        private const val MAX_RETRIES = 10 // Увеличено для мессенджера (доставка важна)

        // Константы ключей для передачи данных в OneTimeWorkRequest
        const val KEY_MESSAGE_ID = "messageId"
        const val KEY_CHAT_ID = "chatId"
        const val KEY_TEXT = "text"
        const val KEY_TIMESTAMP = "scheduledTime"
    }
}
