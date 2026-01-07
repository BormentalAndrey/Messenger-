package com.kakdela.p2p.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Модель контакта для приложения
 */
data class AppContact(
    val name: String,
    val phoneNumber: String,
    val isRegistered: Boolean = false,
    val publicKey: String? = null
)

class ContactP2PManager(
    private val context: Context,
    private val identityRepo: IdentityRepository
) {

    // Временное хранилище для найденных ключей из ответов сети
    private val discoveryResults = ConcurrentHashMap<String, String>()

    /**
     * Основной метод синхронизации.
     * Хеширует номера и ищет их у узлов в сети.
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

        // 3. Настройка слушателя для сбора ответов от сети
        identityRepo.onSignalingMessageReceived = { type, data, fromIp ->
            if (type == "STORE_RESPONSE") {
                // Предполагаем, что данные приходят в формате "key:value" или просто "value"
                // В реальности здесь должна быть логика сопоставления ответа с запросом
                // Для упрощения: если мы искали хеш, мы сохраняем результат
                Log.d("P2P_SYNC", "Received discovery data from $fromIp")
            }
        }

        // 4. Поиск в P2P сети (DHT Lookup)
        // В P2P это асинхронный процесс. Мы рассылаем запросы "FIND".
        localContacts.forEach { contact ->
            val phoneHash = sha256(contact.phoneNumber)
            identityRepo.findPeerInDHT(phoneHash)
        }

        // Даем сети немного времени (2-3 секунды) на ответы от узлов
        delay(2000)

        // 5. Сборка итогового списка
        val syncedList = localContacts.map { contact ->
            val phoneHash = sha256(contact.phoneNumber)
            
            // Проверяем, не пришел ли ответ для этого хеша в репозиторий
            // Примечание: В текущей реализации IdentityRepository данные STORE_RESPONSE 
            // должны оседать в кеше репозитория или передаваться сюда.
            val foundData = discoveryResults[phoneHash] 
            
            if (foundData != null) {
                contact.copy(
                    publicKey = foundData, 
                    isRegistered = true
                )
            } else {
                contact
            }
        }

        // Сортируем: сначала те, кто в сети, затем по алфавиту
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

                if (seenPhones.add(cleanPhone)) {
                    contacts.add(AppContact(name = name, phoneNumber = cleanPhone))
                }
            }
        }
        return contacts
    }

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

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

