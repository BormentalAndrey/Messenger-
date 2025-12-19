package com.kakdela.p2p.vpn.service

import android.content.Context
import androidx.work.*
import com.kakdela.p2p.vpn.core.VpnBackend
import com.kakdela.p2p.vpn.core.WgKeyStore

class AutoRestartWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val backend = VpnBackend(applicationContext)
        val keyStore = WgKeyStore(applicationContext)
        val config = backend.buildWarpConfig(keyStore.getPrivateKey())
        backend.up(config)
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
