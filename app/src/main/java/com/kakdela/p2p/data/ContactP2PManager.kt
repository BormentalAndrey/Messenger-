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
                // Используем репозиторий для генерации хеша, чтобы логика совпадала на 100%
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
                        
                        // Используем единую нормализацию через IdentityRepository (метод должен быть stateless или публичным)
                        // Здесь вызываем identityRepository.normalizePhoneNumber
                        val normalized = identityRepository.normalizePhoneNumber(rawNumber)

                        // Исключаем дубликаты одного и того же номера и слишком короткие номера
                        if (normalized.length >= 5 && seenNumbers.add(normalized)) {
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
}
