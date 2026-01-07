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
 * Класс для управления синхронизацией контактов в P2P сети.
 * Использует хеширование номеров телефонов для защиты приватности при поиске.
 */
class ContactP2PManager(
    private val context: Context,
    private val identityRepo: IdentityRepository
) {

    // Временное хранилище найденных данных: Хеш номера -> "ПубличныйКлюч|IP"
    private val discoveryResults = ConcurrentHashMap<String, String>()

    /**
     * Основной метод синхронизации локальной телефонной книги с P2P сетью.
     */
    suspend fun syncContacts(): List<AppContact> = withContext(Dispatchers.IO) {
        // 1. Проверка разрешений на чтение контактов
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.e("P2P_SYNC", "Permission denied for READ_CONTACTS")
            return@withContext emptyList()
        }

        // 2. Извлечение локальных контактов из ContentResolver
        val localContacts = fetchLocalPhoneContacts()
        if (localContacts.isEmpty()) return@withContext emptyList()

        // Очищаем старые результаты перед новым поиском
        discoveryResults.clear()

        // 3. Установка временного перехватчика сообщений
        // Сохраняем оригинальный слушатель, чтобы не сломать логику чатов
        val originalListener = identityRepo.onSignalingMessageReceived
        
        identityRepo.onSignalingMessageReceived = { type, data, fromIp ->
            if (type == "STORE_RESPONSE") {
                // Ожидаемый формат data от удаленного узла: "sha256PhoneHash:publicKey"
                val parts = data.split(":", limit = 2)
                if (parts.size == 2) {
                    val phoneHash = parts[0]
                    val pubKey = parts[1]
                    // Сохраняем связку: хеш -> ключ и IP адрес отправителя
                    discoveryResults[phoneHash] = "$pubKey|$fromIp"
                    Log.d("P2P_SYNC", "Found peer: $fromIp for hash $phoneHash")
                }
            }
            // Пробрасываем сообщение оригинальному слушателю (например, если пришло сообщение чата)
            originalListener?.invoke(type, data, fromIp)
        }

        

        // 4. Итерация по контактам и запуск DHT поиска
        localContacts.forEach { contact ->
            val phoneHash = sha256(contact.phoneNumber)
            identityRepo.findPeerInDHT(phoneHash)
        }

        // 5. Ожидание ответов от узлов сети (UDP — асинхронный протокол)
        // 2.5 секунды достаточно для локальной сети или стабильного P2P окружения
        delay(2500)

        // Возвращаем слушатель в исходное состояние
        identityRepo.onSignalingMessageReceived = originalListener

        // 6. Сборка итогового списка с сопоставлением найденных данных
        val syncedList = localContacts.map { contact ->
            val phoneHash = sha256(contact.phoneNumber)
            val result = discoveryResults[phoneHash] 
            
            if (result != null) {
                val parts = result.split("|")
                val pubKey = parts[0]
                val ip = parts.getOrNull(1) ?: ""
                
                contact.copy(
                    publicKey = pubKey, 
                    isRegistered = true,
                    lastKnownIp = ip
                )
            } else {
                contact
            }
        }

        // Сортировка: сначала зарегистрированные в приложении, затем по алфавиту
        return@withContext syncedList.sortedWith(
            compareByDescending<AppContact> { it.isRegistered }.thenBy { it.name }
        )
    }

    /**
     * Читает системную базу контактов Android.
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

                // Избегаем дубликатов, если у одного контакта несколько одинаковых номеров
                if (seenPhones.add(cleanPhone)) {
                    contacts.add(AppContact(name = name, phoneNumber = cleanPhone))
                }
            }
        }
        return contacts
    }

    /**
     * Приводит номера телефонов к единому международному стандарту (79991234567).
     */
    private fun normalizePhone(raw: String): String? {
        var phone = raw.replace(Regex("[^0-9]"), "")
        if (phone.isEmpty()) return null
        
        // Логика для РФ/СНГ (замена 8 на 7 и добавление кода страны)
        if (phone.length == 11 && phone.startsWith("8")) {
            phone = "7" + phone.substring(1)
        } else if (phone.length == 10) {
            phone = "7$phone"
        }
        
        return if (phone.length >= 10) phone else null
    }

    /**
     * Создает SHA-256 хеш строки для анонимного поиска в DHT.
     */
    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
    
