package com.kakdela.p2p.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ChatRepository {

    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    fun listenMessages(
        chatId: String,
        onMessages: (List<Message>) -> Unit
    ) {
        listener?.remove()

        listener = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val messages = snapshot.documents.mapNotNull {
                    it.toObject(Message::class.java)
                }
                onMessages(messages)
            }
    }

    fun sendMessage(chatId: String, message: Message) {
        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .add(message)
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }
}
