package com.kakdela.p2p.data

import android.content.Context
import android.provider.ContactsContract
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class ContactManager(private val context: Context) {
    private val db = Firebase.firestore

    suspend fun fetchAndSyncContacts(): List<ContactModel> {
        val phoneContacts = mutableListOf<ContactModel>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, null
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                // Очищаем номер от лишних символов: +7 (999) -> 7999
                val number = it.getString(numberIndex).filter { char -> char.isDigit() }
                phoneContacts.add(ContactModel(name, number))
            }
        }

        // Синхронизация с Firebase (ищем пользователей с такими номерами)
        val syncedContacts = mutableListOf<ContactModel>()
        for (contact in phoneContacts.distinctBy { it.phoneNumber }) {
            val userQuery = db.collection("users")
                .whereEqualTo("phoneNumber", contact.phoneNumber)
                .get().await()

            if (!userQuery.isEmpty) {
                val document = userQuery.documents.first()
                syncedContacts.add(contact.copy(
                    isRegistered = true, 
                    userId = document.id
                ))
            } else {
                syncedContacts.add(contact)
            }
        }
        return syncedContacts.sortedByDescending { it.isRegistered }
    }
}

