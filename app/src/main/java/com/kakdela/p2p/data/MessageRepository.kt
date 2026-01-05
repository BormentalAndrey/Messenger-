package com.kakdela.p2p.data

import android.util.Base64
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.tasks.await

class MessageRepository(private val crypto: CryptoManager) {

    private val db = Firebase.firestore

    // Отправка (Пункт 6.3 - Offline доставка в зашифрованном виде)
    suspend fun sendSecureMessage(
        senderId: String,
        recipientHash: String, // Хеш номера получателя (адрес ящика)
        recipientPublicKey: String,
        text: String
    ) {
        // 1. E2EE Шифрование
        val encryptedBytes = crypto.encryptMessage(text, recipientPublicKey)
        val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

        val envelope = mapOf(
            "sender_id" to senderId,
            "payload" to encryptedBase64, // Сервер не может прочитать это
            "timestamp" to System.currentTimeMillis(),
            "type" to "TEXT_E2EE"
        )

        // 2. Кладем в Inbox получателя (Relay)
        db.collection("dht_identities")
            .document(recipientHash)
            .collection("inbox")
            .add(envelope)
            .await()
    }

    // Прослушка входящих (и расшифровка на лету)
    fun listenInbox(myPhoneHash: String, onMessage: (Message) -> Unit) {
        db.collection("dht_identities")
            .document(myPhoneHash)
            .collection("inbox")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    // Удаляем сообщение с сервера после получения (Пункт 11.2)
                    // (В полноценной реализации удаляем после подтверждения)
                    // change.document.reference.delete() 

                    val data = change.document.data
                    val payload = data["payload"] as String
                    
                    try {
                        // Дешифровка на устройстве
                        val cipherBytes = Base64.decode(payload, Base64.NO_WRAP)
                        val decryptedText = crypto.decryptMessage(cipherBytes)
                        
                        onMessage(Message(
                            text = decryptedText,
                            senderId = data["sender_id"] as String,
                            timestamp = data["timestamp"] as Long,
                            isP2P = false // Пришло через релей
                        ))
                    } catch (e: Exception) {
                        // Не удалось расшифровать (чужой ключ или ошибка)
                        e.printStackTrace()
                    }
                }
            }
    }
}
