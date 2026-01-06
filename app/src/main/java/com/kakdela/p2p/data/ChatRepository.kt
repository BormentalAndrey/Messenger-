package com.kakdela.p2p.data

import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.tasks.await

class MessageRepository(private val crypto: CryptoManager) {
    private val db = FirebaseFirestore.getInstance()

    suspend fun sendSecureMessage(
        senderId: String,
        recipientHash: String,
        recipientPublicKey: String,
        text: String
    ) {
        // Убедитесь, что в CryptoManager есть метод encryptMessage(String, String)
        val encryptedBytes = crypto.encryptMessage(text, recipientPublicKey)
        val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

        val envelope = mapOf(
            "sender_id" to senderId,
            "payload" to encryptedBase64,
            "timestamp" to System.currentTimeMillis(),
            "type" to "TEXT_E2EE"
        )

        db.collection("dht_identities")
            .document(recipientHash)
            .collection("inbox")
            .add(envelope)
            .await()
    }

    fun listenInbox(myPhoneHash: String, onMessage: (Message) -> Unit) {
        db.collection("dht_identities")
            .document(myPhoneHash)
            .collection("inbox")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    val data = change.document.data
                    val payload = data["payload"] as? String ?: return@forEach
                    
                    try {
                        val cipherBytes = Base64.decode(payload, Base64.NO_WRAP)
                        // Убедитесь, что в CryptoManager есть метод decryptMessage(ByteArray)
                        val decryptedText = crypto.decryptMessage(cipherBytes)
                        
                        onMessage(Message(
                            text = decryptedText,
                            senderId = data["sender_id"] as String,
                            timestamp = data["timestamp"] as Long,
                            isP2P = false
                        ))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
    }
}

