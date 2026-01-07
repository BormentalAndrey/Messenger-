package com.kakdela.p2p.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class ContactP2PManager(
    private val context: Context,
    private val identityRepo: IdentityRepository
) {

    /**
     * Основной метод синхронизации.
     * Читает книгу, хеширует номера и ищет их у 2500+ узлов в сети.
     */
    suspend fun syncContacts(): List<AppContact> = withContext(Dispatchers.IO) {
        // 1. Проверка разрешений
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }

        // 2. Получение локальных контактов
        val localContacts = fetchLocalPhoneContacts()
        if (localContacts.isEmpty()) return@withContext emptyList()

        // 3. Поиск в P2P сети (DHT Lookup)
        val syncedList = localContacts.map { contact ->
            val phoneHash = sha256(contact.phoneNumber)
            
            // Ищем данные в распределенной сети по хешу
            val foundData = identityRepo.findPeerInDHT(phoneHash)
            
            if (foundData != null) {
                // Если в DHT лежит JSON, извлекаем ключ и IP
                // В простейшем случае findPeerInDHT возвращает publicKey
                contact.copy(
                    publicKey = foundData, 
                    isRegistered = true
                )
            } else {
                contact
            }
        }

        // Сортируем: сначала те, кто зарегистрирован, затем по алфавиту
        return@withContext syncedList.sortedWith(
            compareByDescending<AppContact> { it.isRegistered }.thenBy { it.name }
        )
    }

    /**
     * Извлекает контакты из Android и нормализует их
     */
    private fun fetchLocalPhoneContacts(): List<AppContact> {
        val contacts = mutableListOf<AppContact>()
        val seenPhones = mutableSetOf<String>()
        
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, null, null, null
        )

        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: "Unknown"
                val rawPhone = it.getString(phoneIdx) ?: continue
                val cleanPhone = normalizePhone(rawPhone) ?: continue

                // Убираем дубликаты
                if (seenPhones.add(cleanPhone)) {
                    contacts.add(AppContact(name = name, phoneNumber = cleanPhone))
                }
            }
        }
        return contacts
    }

    /**
     * Приводит номера к формату 7XXXXXXXXXX
     */
    private fun normalizePhone(raw: String): String? {
        var phone = raw.replace(Regex("[^0-9]"), "")
        if (phone.isEmpty()) return null
        
        if (phone.length == 11 && phone.startsWith("8")) {
            phone = "7" + phone.substring(1)
        } else if (phone.length == 10) {
            phone = "7$phone"
        }
        
        return if (phone.length >= 10) phone else null
    }

    /**
     * Хеширование номера для анонимного поиска в DHT
     */
    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
