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
 * Реализует поиск друзей через сопоставление хэшей номеров (ТЗ п. 2.2).
 */
class ContactP2PManager(
    private val context: Context,
    private val identityRepo: IdentityRepository
) {
    companion object { 
        private const val TAG = "ContactP2PManager" 
    }

    /**
     * Синхронизирует локальную книгу контактов с активными узлами в сети.
     */
    suspend fun syncContacts(): List<AppContact> = withContext(Dispatchers.IO) {
        // 1. Проверка прав
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied for contacts")
            return@withContext emptyList()
        }

        // 2. Получаем список всех активных узлов с сервера api.php (Discovery)
        val onlineNodes: List<UserPayload> = try {
            // Вызываем list_users, который возвращает 2500 последних активных хэшей
            identityRepo.fetchAllNodesFromServer()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch nodes from server: ${e.message}")
            emptyList()
        }

        // 3. Создаем карту для быстрого поиска: phoneHash -> UserData
        val onlineHashMap = onlineNodes.associateBy { it.phone_hash ?: "" }

        // 4. Загружаем контакты из телефонной книги
        val localContacts = fetchLocalPhoneContacts()
        if (localContacts.isEmpty()) return@withContext emptyList()

        // 5. Сопоставляем локальные контакты с онлайн-узлами
        val merged = localContacts.map { contact ->
            // Генерируем хэш номера с солью (PEPPER), как это делает сервер
            val phoneDiscoveryHash = identityRepo.generatePhoneDiscoveryHash(contact.phoneNumber)
            
            val foundPeer = onlineHashMap[phoneDiscoveryHash]

            if (foundPeer != null) {
                // Контакт найден в сети!
                contact.copy(
                    isRegistered = true,
                    userHash = foundPeer.hash, // Security ID для чата
                    publicKey = foundPeer.publicKey,
                    lastKnownIp = foundPeer.ip.orEmpty(),
                    isOnline = true
                )
            } else {
                // Контакт не зарегистрирован или не в сети
                contact.copy(isRegistered = false)
            }
        }

        // 6. Сортируем: сначала те, кто в сети, затем по алфавиту
        return@withContext merged.sortedWith(
            compareByDescending<AppContact> { it.isRegistered }
                .thenBy { it.name.lowercase() }
        )
    }

    /**
     * Извлечение контактов из Android ContactsContract
     */
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
            Log.e(TAG, "Cursor error: ${e.message}") 
        }
        return contacts
    }

    /**
     * Приведение номера к международному формату 10 знаков (ТЗ п. 3.2)
     */
    private fun normalizePhone(raw: String): String? {
        var phone = raw.replace(Regex("[^0-9]"), "")
        if (phone.isEmpty()) return null
        
        // Превращаем 89... в 79...
        if (phone.length == 11 && phone.startsWith("8")) {
            phone = "7" + phone.substring(1)
        }
        
        // Берем только последние 10 цифр для стабильности хэширования
        return if (phone.length >= 10) phone.takeLast(10) else null
    }
}

/**
 * Модель контакта для UI
 */
data class AppContact(
    val name: String,
    val phoneNumber: String,
    val isRegistered: Boolean = false,
    val userHash: String = "",
    val publicKey: String = "",
    val lastKnownIp: String = "",
    val isOnline: Boolean = false
)
