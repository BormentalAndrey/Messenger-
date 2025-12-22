package com.kakdela.p2p.vpn.service

import android.content.Context
import androidx.work.*
import com.kakdela.p2p.vpn.core.VpnBackend
import com.kakdela.p2p.vpn.core.WgKeyStore
import com.kakdela.p2p.vpn.data.ServerRepository
import java.util.concurrent.TimeUnit

class AutoRestartWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val backend = VpnBackend(applicationContext)
            val keyStore = WgKeyStore(applicationContext)

            // 🔹 Берём первый сервер из assets
            val server = ServerRepository(applicationContext)
                .load()
                .first()

            val config = backend.buildConfig(
                privateKey = keyStore.getPrivateKey(),
                server = server
            )

            backend.up(config)
            Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoRestartWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "vpn_auto_restart",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}
