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
 * Управляет синхронизацией телефонных контактов с P2P-сетью.
 * * ✔ Приватность: В сеть уходят только хеши SHA-256.
 * ✔ Совместимость: Исправлен слушатель (4 аргумента).
 * ✔ Производительность: Использование Dispatchers.IO для работы с БД и контактами.
 */
class ContactP2PManager(
    private val context: Context,
    private val identityRepo: IdentityRepository
) {

    companion object {
        private const val TAG = "ContactP2PManager"
    }

    /**
     * phoneHash -> "publicKey|ip"
     */
    private val discoveryResults = ConcurrentHashMap<String, String>()

    /**
     * Основной метод синхронизации контактов.
     * Проверяет локальные контакты и ищет их в DHT/сети.
     */
    suspend fun syncContacts(): List<AppContact> = withContext(Dispatchers.IO) {

        // 1. Проверка разрешений
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "READ_CONTACTS permission not granted")
            return@withContext emptyList()
        }

        // 2. Загрузка локальных контактов
        val localContacts = fetchLocalPhoneContacts()
        if (localContacts.isEmpty()) return@withContext emptyList()

        discoveryResults.clear()

        // 3. Исправленный Слушатель (4 аргумента: type, data, fromIp, fromId)
        val contactListener: (String, String, String, String) -> Unit = { type, data, fromIp, _ ->
            if (type == "STORE_RESPONSE") {
                try {
                    // Ожидаемый формат данных: "<phoneHash>:<publicKey>"
                    val parts = data.split(":", limit = 2)
                    if (parts.size == 2) {
                        val phoneHash = parts[0]
                        val publicKey = parts[1]
                        discoveryResults[phoneHash] = "$publicKey|$fromIp"
                        Log.d(TAG, "Found contact match: $phoneHash at $fromIp")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing STORE_RESPONSE", e)
                }
            }
        }

        // Регистрируем слушатель в репозитории
        identityRepo.addListener(contactListener)

        try {
            // 4. Запуск поиска в DHT для каждого контакта
            localContacts.forEach { contact ->
                val hash = sha256(contact.phoneNumber)
                // Запускаем асинхронный поиск
                identityRepo.findPeerInDHT(hash)
            }

            // 5. Ожидание ответов по UDP (асинхронная природа сети)
            delay(2500)

        } finally {
            // Обязательно удаляем слушатель, чтобы не было утечек памяти
            identityRepo.removeListener(contactListener)
        }

        // 6. Слияние (Merge) локальных данных и результатов поиска
        val merged = localContacts.map { contact ->
            val hash = sha256(contact.phoneNumber)
            val foundData = discoveryResults[hash]

            if (foundData != null) {
                val parts = foundData.split("|", limit = 2)
                val pubKey = parts.getOrNull(0).orEmpty()
                val ip = parts.getOrNull(1).orEmpty()

                contact.copy(
                    isRegistered = true,
                    publicKey = pubKey,
                    lastKnownIp = ip
                )
            } else {
                contact
            }
        }

        // 7. Сортировка: сначала зарегистрированные, затем по алфавиту
        return@withContext merged.sortedWith(
            compareByDescending<AppContact> { it.isRegistered }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun fetchLocalPhoneContacts(): List<AppContact> {
        val contacts = mutableListOf<AppContact>()
        val seenPhones = HashSet<String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: "Unknown"
                    val rawPhone = cursor.getString(phoneIdx) ?: continue
                    val phone = normalizePhone(rawPhone) ?: continue

                    if (seenPhones.add(phone)) {
                        contacts.add(AppContact(name = name, phoneNumber = phone))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching contacts", e)
        }

        return contacts
    }

    private fun normalizePhone(raw: String): String? {
        var phone = raw.replace(Regex("[^0-9]"), "")
        if (phone.isEmpty()) return null

        // Унификация форматов для СНГ (пример)
        if (phone.length == 11 && phone.startsWith("8")) {
            phone = "7" + phone.substring(1)
        } else if (phone.length == 10) {
            phone = "7$phone"
        }

        return if (phone.length >= 10) phone else null
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
