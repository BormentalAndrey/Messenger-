package com.kakdela.p2p.vpn.service

import android.content.Context
import androidx.work.*
import com.kakdela.p2p.vpn.core.VpnBackend

class AutoRestartWorker(app: Context, params: WorkerParameters) :
    CoroutineWorker(app, params) {

    override suspend fun doWork(): Result {
        VpnBackend(applicationContext).safeReconnect()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoRestartWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "vpn_restart",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
    }
