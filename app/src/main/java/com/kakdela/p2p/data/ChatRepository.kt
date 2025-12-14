package com.kakdela.p2p.data

import com.google.firebase.firestore.FirebaseFirestore

class ChatRepository {

    private val db = FirebaseFirestore.getInstance()

    fun listen(chatId: String, onUpdate: (List<Message>) -> Unit) {
        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .addSnapshotListener { snapshot, _ ->
                onUpdate(snapshot?.toObjects(Message::class.java) ?: emptyList())
            }
    }

    fun send(chatId: String, message: Message) {
        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .add(message)
    }
}
