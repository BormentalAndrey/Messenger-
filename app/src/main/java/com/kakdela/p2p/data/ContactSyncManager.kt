package com.kakdela.p2p.data

import android.content.Context
import android.provider.ContactsContract
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class ContactSyncManager(private val context: Context) {

    suspend fun syncContacts(): List<AppContact> {

        val contacts = mutableListOf<AppContact>()
        val phoneSet = mutableSetOf<String>()

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

        cursor?.use {
            while (it.moveToNext()) {

                val name = it.getString(
                    it.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    )
                ) ?: "Без имени"

                val rawPhone = it.getString(
                    it.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                ) ?: continue

                val phone = normalizePhone(rawPhone) ?: continue

                // ❗ исключаем дубликаты +7 / 8 / 7
                if (phoneSet.add(phone)) {
                    contacts.add(
                        AppContact(
                            name = name,
                            phoneNumber = phone
                        )
                    )
                }
            }
        }

        if (contacts.isEmpty()) return emptyList()

        // 🔎 поиск зарегистрированных
        val snapshots = Firebase.firestore
            .collection("users")
            .whereIn("phoneNumber", contacts.map { it.phoneNumber })
            .get()
            .await()

        val registeredMap = snapshots.documents.associate { doc ->
            doc.getString("phoneNumber") to doc.id
        }

        return contacts.map { contact ->
            val uid = registeredMap[contact.phoneNumber]
            if (uid != null) {
                contact.copy(uid = uid, isRegistered = true)
            } else {
                contact
            }
        }
    }

    // ⭐ КЛЮЧЕВАЯ ФУНКЦИЯ
    private fun normalizePhone(raw: String): String? {
        var phone = raw.replace(Regex("[^\\d]"), "")
        if (phone.length < 10) return null
        return phone.takeLast(10)
    }
}
