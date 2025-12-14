package com.kakdela.p2p.data

import android.content.Context
import android.provider.ContactsContract
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class ContactSync(private val context: Context) {

    suspend fun syncContacts(currentUserId: String): List<String> {
        val phoneNumbers = mutableSetOf<String>()

        // Читаем контакты
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                var phone = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                phone = phone.replace(Regex("[^\\d]"), "") // Только цифры
                if (phone.startsWith("8")) phone = "7" + phone.drop(1)
                if (phone.length >= 11) phone = phone.drop(phone.length - 11)
                if (phone.startsWith("7") && phone.length == 11) {
                    phoneNumbers.add(phone)
                }
            }
        }

        val registeredUsers = mutableListOf<String>()

        if (phoneNumbers.isNotEmpty()) {
            val snapshots = Firebase.firestore.collection("users")
                .whereIn("phoneNumber", phoneNumbers.toList())
                .get()
                .await()

            for (doc in snapshots.documents) {
                val uid = doc.id
                if (uid != currentUserId) {
                    registeredUsers.add(uid)
                }
            }
        }

        // Теперь создаём личные чаты с найденными пользователями
        createPersonalChats(currentUserId, registeredUsers)

        return registeredUsers
    }

    private suspend fun createPersonalChats(currentUserId: String, targetUserIds: List<String>) {
        val db = Firebase.firestore

        for (targetId in targetUserIds) {
            val participants = listOf(currentUserId, targetId).sorted()
            val chatId = participants.joinToString("_")

            val chatRef = db.collection("chats").document(chatId)
            val snapshot = chatRef.get().await()

            if (!snapshot.exists()) {
                chatRef.set(mapOf(
                    "participantIds" to participants,
                    "title" to "Контакт из телефона",
                    "lastMessage" to "",
                    "timestamp" to com.google.firebase.Timestamp.now()
                )).await()
            }
        }
    }
}
