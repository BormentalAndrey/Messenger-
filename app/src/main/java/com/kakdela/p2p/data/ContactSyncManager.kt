package com.kakdela.p2p.data

import android.content.Context
import android.provider.ContactsContract
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class ContactSyncManager(private val context: Context) {

    suspend fun syncContacts(): List<AppContact> {
        val contacts = mutableListOf<AppContact>()

        // Читаем контакты из телефонной книги
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        val phoneSet = mutableSetOf<String>()

        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: "Без имени"
                var phone = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: continue

                // Нормализуем номер
                phone = phone.replace(Regex("[^\\d]"), "")
                if (phone.startsWith("8") && phone.length == 11) phone = "7" + phone.drop(1)
                if (phone.length > 11) phone = phone.takeLast(11)
                if (phone.length != 11 || !phone.startsWith("7")) continue

                if (phoneSet.add(phone)) {  // Избегаем дубликатов
                    contacts.add(AppContact(name = name, phoneNumber = phone))
                }
            }
        }

        // Находим зарегистрированных пользователей
        if (contacts.isNotEmpty()) {
            val phoneNumbers = contacts.map { it.phoneNumber }

            val snapshots = Firebase.firestore.collection("users")
                .whereIn("phoneNumber", phoneNumbers)
                .get()
                .await()

            val registeredMap = snapshots.documents.associate { doc ->
                val phone = doc.getString("phoneNumber") ?: ""
                phone to doc.id
            }

            contacts.forEach { contact ->
                registeredMap[contact.phoneNumber]?.let { uid ->
                    contact.copy(uid = uid, isRegistered = true)
                }
            }
        }

        return contacts.sortedBy { it.name.lowercase() }
    }
}
