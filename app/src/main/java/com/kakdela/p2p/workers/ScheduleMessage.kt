package com.kakdela.p2p.workers

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Утилитарная функция для регистрации отложенной отправки сообщения в WorkManager.
 * Гарантирует запуск задачи даже после перезагрузки устройства (Persistence).
 * * Исправлено: использование актуальной константы MIN_BACKOFF_MILLIS из WorkRequest.
 */
fun scheduleMessageWork(
    context: Context,
    messageId: String,
    chatId: String,
    text: String,
    scheduledTime: Long
) {
    // 1. Вычисляем задержку перед выполнением
    val currentTime = System.currentTimeMillis()
    val delay = scheduledTime - currentTime
    
    // Если время уже наступило или в прошлом, воркер не нужен (отправка идет через Repository напрямую)
    if (delay <= 0) return

    // 2. Формируем входные данные (Input Data)
    // Ключи строго соответствуют тем, что считывает ScheduledMessageWorker
    val data = Data.Builder()
        .putString("messageId", messageId)
        .putString("chatId", chatId)
        .putString("text", text)
        .putLong("scheduledTime", scheduledTime)
        .build()

    // 3. Устанавливаем ограничения (Constraints)
    // Не пытаемся отправить, если нет доступа к сети
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // 4. Создаем запрос на выполнение задачи (OneTimeWorkRequest)
    val request = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setInputData(data)
        .setConstraints(constraints)
        // Экспоненциальная стратегия повтора при сбоях (например, если пир внезапно ушел в оффлайн)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            WorkRequest.MIN_BACKOFF_MILLIS, // Исправленная ссылка на константу
            TimeUnit.MILLISECONDS
        )
        .addTag("scheduled_msg_tag") // Общий тег для массовых операций
        .addTag(messageId)           // Индивидуальный тег для точечного управления
        .build()

    // 5. Постановка в очередь уникальной задачи
    // ExistingWorkPolicy.REPLACE позволяет обновить время отправки, если пользователь его изменил
    WorkManager.getInstance(context)
        .enqueueUniqueWork(
            messageId, // Уникальный идентификатор задачи привязан к ID сообщения
            ExistingWorkPolicy.REPLACE,
            request
        )
}

/**
 * Функция для отмены запланированного сообщения.
 * Вызывается, если пользователь удалил сообщение или отредактировал его, превратив в немедленное.
 */
fun cancelScheduledMessage(context: Context, messageId: String) {
    WorkManager.getInstance(context).cancelUniqueWork(messageId)
}
