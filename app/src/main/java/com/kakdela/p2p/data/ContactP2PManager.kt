package com.kakdela.p2p.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.kakdela.p2p.api.UserPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Менеджер контактов P2P.
 * Сопоставляет локальную книгу с данными из api.php (ТЗ п. 2.2).
 */
class ContactP2PManager(
    private val context: Context,
    private val identityRepo: IdentityRepository
) {
    companion object { 
        private const val TAG = "ContactP2PManager" 
    }

    /**
     * Синхронизирует контакты. Использует метод O(N) через HashMap.
     */
    suspend fun syncContacts(): List<AppContact> = withContext(Dispatchers.IO) {
        // 1. Проверка разрешений
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing READ_CONTACTS permission")
            return@withContext emptyList()
        }

        // 2. Получение списка всех "живых" хэшей с сервера
        val onlineNodes: List<UserPayload> = try {
            identityRepo.fetchAllNodesFromServer()
        } catch (e: Exception) {
            Log.e(TAG, "Discovery sync failed: ${e.message}")
            emptyList()
        }

        // 3. Карта для мгновенного поиска по phone_hash (Discovery ID)
        val onlineHashMap = onlineNodes.associateBy { it.phone_hash ?: "" }

        // 4. Сбор локальных контактов
        val localContacts = fetchLocalPhoneContacts()
        
        // 5. Сопоставление
        val merged = localContacts.map { contact ->
            // Генерируем хэш номера с PEPPER (как в api.php)
            val phoneDiscoveryHash = identityRepo.generatePhoneDiscoveryHash(contact.phoneNumber)
            
            val peer = onlineHashMap[phoneDiscoveryHash]

            if (peer != null) {
                contact.copy(
                    isRegistered = true,
                    userHash = peer.hash,         // Сохраняем Security ID для NavGraph
                    publicKey = peer.publicKey,
                    lastKnownIp = peer.ip,
                    isOnline = true
                )
            } else {
                contact.copy(isRegistered = false)
            }
        }

        // 6. Сортировка: Сначала зарегистрированные, затем по алфавиту
        merged.sortedWith(
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
                projection, null, null, null
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
            Log.e(TAG, "Contacts query error: ${e.message}") 
        }
        return contacts
    }

    private fun normalizePhone(raw: String): String? {
        var phone = raw.replace(Regex("[^0-9]"), "")
        if (phone.isEmpty()) return null
        
        if (phone.length == 11 && phone.startsWith("8")) {
            phone = "7" + phone.substring(1)
        }
        
        // Для хэширования берем последние 10 цифр (единый формат в ТЗ)
        return if (phone.length >= 10) phone.takeLast(10) else null
    }
}
