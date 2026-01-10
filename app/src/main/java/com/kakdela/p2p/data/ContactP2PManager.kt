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
 * Менеджер для синхронизации локальной телефонной книги с P2P узлами.
 * * ВНИМАНИЕ: Определение AppContact удалено отсюда, так как оно находится 
 * в отдельном файле AppContact.kt для предотвращения ошибки "Redeclaration".
 */
class ContactP2PManager(
    private val context: Context,
    private val identityRepository: IdentityRepository
) {
    private val TAG = "ContactP2PManager"

    /**
     * Основной метод синхронизации.
     * Сопоставляет локальные номера телефонов с хешами пользователей на сервере.
     */
    suspend fun syncContacts(): List<AppContact> = withContext(Dispatchers.IO) {
        // Проверка разрешений
        if (ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Permission READ_CONTACTS not granted")
            return@withContext emptyList()
        }

        try {
            // 1. Получаем актуальный список всех узлов из сети (DHT/Server)
            val allNodes: List<UserPayload> = identityRepository.fetchAllNodesFromServer()
            
            // Фильтруем только тех, у кого есть хеш телефона, и создаем карту для быстрого поиска
            val onlineMap = allNodes
                .filter { !it.phone_hash.isNullOrBlank() }
                .associateBy { it.phone_hash!! }

            // 2. Читаем контакты из памяти телефона
            val localContacts = fetchLocalContacts()

            // 3. Выполняем сопоставление (Matching)
            val mergedContacts = localContacts.map { contact ->
                // Генерируем поиск-хеш через репозиторий (важно использовать тот же PEPPER)
                val phoneHash = identityRepository.generatePhoneDiscoveryHash(contact.phoneNumber)
                
                val peer = onlineMap[phoneHash]

                if (peer != null) {
                    // Узел найден в сети
                    contact.copy(
                        userHash = peer.hash,
                        publicKey = peer.publicKey,
                        lastKnownIp = peer.ip,
                        isOnline = (System.currentTimeMillis() - (peer.lastSeen ?: 0)) < 300_000, // Онлайн если был виден последние 5 мин
                        isRegistered = true
                    )
                } else {
                    contact
                }
            }

            // 4. Сортировка: Приоритет Online -> Registered -> Alphabetical
            return@withContext mergedContacts.sortedWith(
                compareByDescending<AppContact> { it.isOnline }
                    .thenByDescending { it.isRegistered }
                    .thenBy { it.name.lowercase() }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Sync contacts failed: ${e.message}")
            return@withContext emptyList()
        }
    }

    /**
     * Прямое чтение из ContentResolver
     */
    private fun fetchLocalContacts(): List<AppContact> {
        val result = mutableListOf<AppContact>()
        val seenNumbers = HashSet<String>()
        
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
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                if (nameIdx >= 0 && numberIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIdx) ?: "Unknown"
                        val rawNumber = cursor.getString(numberIdx) ?: continue
                        
                        // Нормализуем номер (приводим к формату 7XXXXXXXXXX)
                        val normalized = normalizePhone(rawNumber) ?: continue

                        // Исключаем дубликаты одного и того же номера
                        if (seenNumbers.add(normalized)) {
                            result.add(
                                AppContact(
                                    name = name,
                                    phoneNumber = normalized
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cursor error", e)
        }
        return result
    }

    /**
     * Приведение номера к международному стандарту без знака +
     * Работает синхронно с логикой в IdentityRepository
     */
    private fun normalizePhone(raw: String): String? {
        // Убираем всё кроме цифр
        var digits = raw.replace(Regex("[^0-9]"), "")

        return when {
            // Если номер начинается с 89... (РФ) -> заменяем на 79...
            digits.length == 11 && digits.startsWith("8") -> "7" + digits.substring(1)
            // Если номер 10 цифр и начинается с 9... -> добавляем 7
            digits.length == 10 && digits.startsWith("9") -> "7$digits"
            // Если уже корректный 7XXXXXXXXXX
            digits.length == 11 && digits.startsWith("7") -> digits
            // Прочие международные номера оставляем как есть, если они длинные
            digits.length >= 11 -> digits
            else -> null
        }
    }
}
