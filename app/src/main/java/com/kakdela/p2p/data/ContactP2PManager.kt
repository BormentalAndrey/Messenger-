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
 * Исправлена логика нормализации для точного совпадения хэшей.
 */
class ContactP2PManager(
    private val context: Context,
    private val identityRepo: IdentityRepository
) {
    companion object { 
        private const val TAG = "ContactP2PManager" 
    }

    /**
     * Основной метод синхронизации.
     * Сверяет локальные номера с базой активных узлов на сервере.
     */
    suspend fun syncContacts(): List<AppContact> = withContext(Dispatchers.IO) {
        // 1. Проверка разрешений перед доступом к ContentResolver
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Ошибка: Разрешение READ_CONTACTS не предоставлено")
            return@withContext emptyList()
        }

        // 2. Получение списка активных пользователей с сервера Discovery
        val onlineNodes: List<UserPayload> = try {
            identityRepo.fetchAllNodesFromServer()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка синхронизации с сервером: ${e.message}")
            emptyList()
        }

        // 3. Создаем карту для быстрого поиска O(1). Ключ — phone_hash.
        val onlineHashMap = onlineNodes.associateBy { it.phone_hash ?: "" }

        // 4. Читаем контакты из телефонной книги
        val localContacts = fetchLocalPhoneContacts()
        
        // 5. Процесс сопоставления (Matching)
        val merged = localContacts.map { contact ->
            // Генерируем хэш от НОРМАЛИЗОВАННОГО номера (7900...)
            val phoneDiscoveryHash = identityRepo.generatePhoneDiscoveryHash(contact.phoneNumber)
            
            val peer = onlineHashMap[phoneDiscoveryHash]

            if (peer != null) {
                // Пользователь найден в сети
                contact.copy(
                    isRegistered = true,
                    userHash = peer.hash,         // ID для маршрутизации сообщений
                    publicKey = peer.publicKey,
                    lastKnownIp = peer.ip,
                    isOnline = true               // Узел активен
                )
            } else {
                // Пользователь не зарегистрирован или не в сети
                contact.copy(isRegistered = false, isOnline = false)
            }
        }

        // 6. Финальная сортировка: Сначала онлайн-контакты, затем остальные по алфавиту
        merged.sortedWith(
            compareByDescending<AppContact> { it.isRegistered }
                .thenBy { it.name.lowercase() }
        )
    }

    /**
     * Извлекает контакты из Android Contacts Provider.
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
                    
                    // Важный этап: приведение любого ввода к стандарту 79XXXXXXXXX
                    val normalized = normalizePhone(rawPhone) ?: continue

                    // Исключаем дубликаты, если один контакт записан несколько раз
                    if (seenPhones.add(normalized)) {
                        contacts.add(AppContact(name = name, phoneNumber = normalized))
                    }
                }
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Ошибка чтения контактов: ${e.message}") 
        }
        return contacts
    }

    /**
     * Продакшн-логика нормализации номера:
     * 1. Убирает лишние символы.
     * 2. Превращает 8... в 7...
     * 3. Добавляет 7 к 10-значным номерам.
     * Это гарантирует, что хэш в приложении совпадет с хэшем в MySQL.
     */
    private fun normalizePhone(raw: String): String? {
        // Очистка от скобок, тире и пробелов
        var digits = raw.replace(Regex("[^0-9]"), "")
        if (digits.isEmpty()) return null
        
        // Корректировка префикса РФ/СНГ
        digits = when {
            digits.length == 11 && digits.startsWith("8") -> "7" + digits.substring(1)
            digits.length == 10 && digits.startsWith("9") -> "7" + digits
            else -> digits
        }
        
        // Проверяем минимально допустимую длину (для РФ это 11 цифр)
        return if (digits.length >= 11) digits else null
    }
}
