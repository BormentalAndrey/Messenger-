package com.kakdela.p2p.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class ContactManager(private val context: Context) {

    private val db = Firebase.firestore

    suspend fun fetchAndSyncContacts(): List<ContactModel> {

        // 🔐 1. ПРОВЕРКА РАЗРЕШЕНИЯ
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val phoneContacts = mutableListOf<ContactModel>()

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )

        cursor?.use {
            val nameIndex = it.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val numberIndex = it.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: "Unknown"
                val rawNumber = it.getString(numberIndex) ?: ""
                val cleanNumber = rawNumber.filter(Char::isDigit)

                if (cleanNumber.isNotEmpty()) {
                    phoneContacts.add(ContactModel(name, cleanNumber))
                }
            }
        }

        val syncedContacts = mutableListOf<ContactModel>()

        for (contact in phoneContacts.distinctBy { it.phoneNumber }) {
            try {
                val userQuery = db.collection("users")
                    .whereEqualTo("phoneNumber", contact.phoneNumber)
                    .limit(1)
                    .get()
                    .await()

                if (!userQuery.isEmpty) {
                    val doc = userQuery.documents.first()
                    syncedContacts.add(
                        contact.copy(
                            isRegistered = true,
                            userId = doc.id
                        )
                    )
                } else {
                    syncedContacts.add(contact)
                }
            } catch (_: Exception) {
                // 🔕 Firestore не должен ломать UX
                syncedContacts.add(contact)
            }
        }

        return syncedContacts.sortedByDescending { it.isRegistered }
    }
}
