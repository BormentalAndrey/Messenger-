package com.kakdela.p2p.data

import android.content.Context
import android.provider.ContactsContract
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ContactSyncManager(private val context: Context) {

    suspend fun syncContacts(): List<AppContact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<AppContact>()
        val phoneSet = mutableSetOf<String>()

        // Запрос к контактам телефона
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
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = if (nameIndex >= 0) it.getString(nameIndex) else "Без имени"
                val rawPhone = if (numberIndex >= 0) it.getString(numberIndex) else continue
                
                val phone = normalizePhone(rawPhone) ?: continue

                // Исключаем дубликаты номеров локально
                if (phoneSet.add(phone)) {
                    contacts.add(AppContact(name = name, phoneNumber = phone))
                }
            }
        }

        if (contacts.isEmpty()) return@withContext emptyList()

        // --- ИСПРАВЛЕНИЕ: Разбиваем запрос на пачки по 10 номеров ---
        val registeredMap = mutableMapOf<String, String>() // phone -> uid
        val phoneNumbersToCheck = contacts.map { it.phoneNumber }
        
        // Firestore имеет лимит: максимум 10 значений в операторе 'IN'
        val chunks = phoneNumbersToCheck.chunked(10)

        for (chunk in chunks) {
            try {
                val snapshot = Firebase.firestore
                    .collection("users")
                    .whereIn("phoneNumber", chunk)
                    .get()
                    .await()
                
                for (doc in snapshot.documents) {
                    val ph = doc.getString("phoneNumber")
                    if (ph != null) {
                        registeredMap[ph] = doc.id
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Продолжаем выполнение, даже если одна пачка не загрузилась
            }
        }

        // Сопоставляем локальные контакты с найденными UID
        return@withContext contacts.map { contact ->
            val uid = registeredMap[contact.phoneNumber]
            if (uid != null) {
                contact.copy(uid = uid, isRegistered = true)
            } else {
                contact
            }
        }.sortedByDescending { it.isRegistered }
    }

    private fun normalizePhone(raw: String): String? {
        // Оставляем только цифры
        var phone = raw.replace(Regex("[^\\d]"), "")
        
        // Коррекция для РФ: если 11 цифр и начинается с 8 -> меняем на 7
        if (phone.length == 11 && phone.startsWith("8")) {
            phone = "7" + phone.substring(1)
        }
        
        // Если номер слишком короткий, игнорируем
        if (phone.length < 10) return null
        
        // Возвращаем полный формат (например, 79990000000)
        // Если номер был без кода страны (10 цифр), добавляем 7 (предполагаем РФ)
        return if (phone.length == 10) {
            "7$phone"
        } else {
             phone 
        }
    }
}

