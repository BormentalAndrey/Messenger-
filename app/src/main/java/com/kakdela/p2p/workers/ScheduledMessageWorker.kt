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
 * Воркер для выполнения отложенной отправки сообщений.
 * Работает в фоновом режиме, даже если приложение закрыто.
 */
class ScheduledMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    // Инъекция репозиториев через Koin
    private val messageRepository: MessageRepository by inject()
    private val identityRepository: IdentityRepository by inject()

    override suspend fun doWork(): Result {
        // 1. Извлечение данных из InputData (ключи должны совпадать с ScheduleMessage.kt)
        val messageId = inputData.getString("messageId") 
            ?: return Result.failure().also { Log.e(TAG, "Ошибка: Отсутствует ID сообщения") }
        
        val chatId = inputData.getString("chatId") 
            ?: return Result.failure().also { Log.e(TAG, "Ошибка: Отсутствует ID чата") }
        
        val text = inputData.getString("text") 
            ?: return Result.failure().also { Log.e(TAG, "Ошибка: Отсутствует текст сообщения") }

        Log.d(TAG, "Запуск отложенной отправки для сообщения: $messageId")

        return try {
            // 2. Получаем актуальные данные о пире (телефон и IP) непосредственно перед отправкой
            // Это важно, так как за время ожидания узел мог сменить IP или появиться в кеше
            val peerNode = identityRepository.getCachedNode(chatId)
            val receiverPhone = peerNode?.phone

            // 3. Попытка сетевой отправки (P2P UDP -> SMS Fallback)
            // Используем метод из MessageRepository, который мы обновили ранее
            val isDelivered = messageRepository.performNetworkSend(
                chatId = chatId,
                messageId = messageId,
                payload = text,
                phone = receiverPhone
            )

            if (isDelivered) {
                Log.i(TAG, "Отложенное сообщение $messageId успешно доставлено")
                Result.success()
            } else {
                // Если не доставлено, проверяем количество попыток
                if (runAttemptCount < MAX_RETRIES) {
                    Log.w(TAG, "Доставка не удалась, попытка #$runAttemptCount. Повтор...")
                    Result.retry()
                } else {
                    Log.e(TAG, "Превышено количество попыток отправки для $messageId")
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка при выполнении воркера", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "ScheduledWorker"
        private const val MAX_RETRIES = 5 // Увеличено для большей надежности в продакшне

        // Константы ключей (для совместимости, если используются в других местах)
        const val KEY_MESSAGE_ID = "messageId"
        const val KEY_CHAT_ID = "chatId"
        const val KEY_TEXT = "text"
        const val KEY_TIMESTAMP = "scheduledTime"
    }
}
