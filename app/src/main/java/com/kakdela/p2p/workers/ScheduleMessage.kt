package com.kakdela.p2p.workers

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

fun scheduleMessageWork(
    context: Context,
    messageId: String,
    chatId: String,
    text: String,
    scheduledTime: Long
) {
    val delay = scheduledTime - System.currentTimeMillis()
    if (delay <= 0) return

    val data = Data.Builder()
        .putString(ScheduledMessageWorker.KEY_MESSAGE_ID, messageId)
        .putString(ScheduledMessageWorker.KEY_CHAT_ID, chatId)
        .putString(ScheduledMessageWorker.KEY_TEXT, text)
        .putLong(ScheduledMessageWorker.KEY_TIMESTAMP, scheduledTime)
        .build()

    val request = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setInputData(data)
        .addTag("scheduled_message_$messageId")
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork(
            "scheduled_message_$messageId",
            ExistingWorkPolicy.REPLACE,
            request
        )
}
