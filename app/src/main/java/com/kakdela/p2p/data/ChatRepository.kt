package com.kakdela.p2p.data

import com.google.firebase.firestore.FirebaseFirestore

class ChatRepository {

    private val db = FirebaseFirestore.getInstance()

    fun sendMessage(chatId: String, message: Message) {
        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .add(message)
    }

    fun listenMessages(chatId: String, onUpdate: (List<Message>) -> Unit) {
        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    onUpdate(snapshot.toObjects(Message::class.java))
                }
            }
    }
}
