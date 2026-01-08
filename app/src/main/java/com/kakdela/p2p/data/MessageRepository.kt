package com.kakdela.p2p.data

import com.google.firebase.firestore.FirebaseFirestore
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.tasks.await

class MessageRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun sendSecureMessage(
        senderId: String,
        recipientHash: String,
        recipientPublicKey: String,
        text: String
    ) {
        // CryptoManager.encryptMessage возвращает уже готовую Base64 строку
        val encryptedBase64 = CryptoManager.encryptMessage(text, recipientPublicKey)

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
                    
                    // Метод decryptMessage принимает строку и возвращает строку
                    val decryptedText = CryptoManager.decryptMessage(payload)
                    
                    onMessage(Message(
                        id = change.document.id,
                        text = decryptedText,
                        senderId = data["sender_id"] as String,
                        timestamp = data["timestamp"] as Long,
                        isMe = false
                    ))
                }
            }
    }
}
