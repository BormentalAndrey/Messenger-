package com.kakdela.p2p.workers

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Утилитарная функция для регистрации отложенной отправки сообщения в WorkManager.
 * Гарантирует доставку даже после перезагрузки устройства или закрытия приложения.
 */
fun scheduleMessageWork(
    context: Context,
    messageId: String,
    chatId: String,
    text: String,
    scheduledTime: Long
) {
    // 1. Вычисляем задержку
    val delay = scheduledTime - System.currentTimeMillis()
    
    // Если время уже прошло, отправка должна была произойти немедленно (обработка в Repository)
    if (delay <= 0) return

    // 2. Формируем входные данные для воркера
    // Используем строковые ключи, которые гарантированно распознает ScheduledMessageWorker
    val data = Data.Builder()
        .putString("messageId", messageId)
        .putString("chatId", chatId)
        .putString("text", text)
        .putLong("scheduledTime", scheduledTime)
        .build()

    // 3. Устанавливаем ограничения (Constraints)
    // Сообщение должно отправляться только при наличии интернет-соединения
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // 4. Создаем запрос на выполнение задачи
    val request = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setInputData(data)
        .setConstraints(constraints)
        // Добавляем экспоненциальную политику отката при неудаче (если сеть пропала в момент отправки)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
            TimeUnit.MILLISECONDS
        )
        .addTag("scheduled_msg_tag") // Общий тег для мониторинга
        .addTag(messageId)           // Уникальный тег для возможности отмены конкретного сообщения
        .build()

    // 5. Постановка в очередь
    // Используем UniqueWork с политикой REPLACE, чтобы избежать дубликатов при редактировании времени
    WorkManager.getInstance(context)
        .enqueueUniqueWork(
            messageId, // Уникальное имя задачи — это ID сообщения
            ExistingWorkPolicy.REPLACE,
            request
        )
}

/**
 * Функция для отмены запланированного сообщения (если пользователь удалил его до отправки)
 */
fun cancelScheduledMessage(context: Context, messageId: String) {
    WorkManager.getInstance(context).cancelUniqueWork(messageId)
}
