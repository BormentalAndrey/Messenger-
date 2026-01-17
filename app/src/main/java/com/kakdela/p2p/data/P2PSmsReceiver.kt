package com.kakdela.p2p.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class P2PSmsReceiver : BroadcastReceiver() {
    private val TAG = "P2PSmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (sms in messages) {
            val body = sms.displayMessageBody
            if (body.startsWith("P2P:")) {
                processP2PSms(context, body.removePrefix("P2P:"), sms.originatingAddress)
            }
        }
    }

    private fun processP2PSms(context: Context, data: String, senderPhone: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Разбираем строку: hash|ip|port|pubKey
                val parts = data.split("|")
                if (parts.size < 4) return@launch

                val hash = parts[0]
                val ip = parts[1]
                val port = parts[2].toInt()
                val pubKey = parts[3]

                val db = ChatDatabase.getDatabase(context)
                val nodeDao = db.nodeDao()

                // Сохраняем узел в базу
                nodeDao.upsert(NodeEntity(
                    userHash = hash,
                    phone_hash = "", // Можно сгенерировать из senderPhone
                    ip = ip,
                    port = port,
                    publicKey = pubKey,
                    phone = senderPhone ?: "",
                    lastSeen = System.currentTimeMillis()
                ))

                Log.i(TAG, "New node discovered via SMS: $hash at $ip")

                // Сразу пытаемся "простучать" его по UDP, если интернет появился
                // (IdentityRepository подхватит его при следующем цикле pingKnownNodes)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing P2P SMS", e)
            }
        }
    }
}
