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

// Модель для отображения в UI
data class AppContact(
    val name: String,
    val phoneNumber: String,
    val userHash: String? = null,
    val publicKey: String? = null,
    val lastKnownIp: String? = null,
    val isOnline: Boolean = false,
    val isRegistered: Boolean = false
)

class ContactP2PManager(
    private val context: Context,
    private val identityRepository: IdentityRepository
) {
    private val TAG = "ContactP2PManager"

    suspend fun syncContacts(): List<AppContact> = withContext(Dispatchers.IO) {

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }

        // 1. Получаем список всех зарегистрированных пользователей с сервера
        val onlineUsers: List<UserPayload> = identityRepository.fetchAllNodesFromServer()
            .filter { !it.phone_hash.isNullOrBlank() }

        // Map для быстрого поиска: phone_hash -> UserPayload
        val onlineMap = onlineUsers.associateBy { it.phone_hash!! }

        // 2. Читаем локальную телефонную книгу
        val localContacts = fetchLocalContacts()

        // 3. Сопоставляем
        val mergedContacts = localContacts.map { contact ->
            // Генерируем хэш для этого номера так же, как это делается при регистрации
            val phoneHash = identityRepository.generatePhoneDiscoveryHash(contact.phoneNumber)
            
            val peer = onlineMap[phoneHash]

            if (peer != null) {
                contact.copy(
                    userHash = peer.hash,
                    publicKey = peer.publicKey,
                    lastKnownIp = peer.ip,
                    isOnline = true, // Считаем онлайн, если нашли на сервере (можно уточнить по lastSeen)
                    isRegistered = true
                )
            } else {
                contact
            }
        }

        // 4. Сортировка: Сначала онлайн, потом зарегистрированные, потом остальные по имени
        mergedContacts.sortedWith(
            compareByDescending<AppContact> { it.isOnline }
                .thenByDescending { it.isRegistered }
                .thenBy { it.name.lowercase() }
        )
    }

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
                projection, null, null, null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                if (nameIdx >= 0 && numberIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIdx) ?: "Unknown"
                        val rawNumber = cursor.getString(numberIdx) ?: continue
                        val normalized = normalizePhone(rawNumber) ?: continue

                        if (seenNumbers.add(normalized)) {
                            result.add(AppContact(name, normalized))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading contacts", e)
        }
        return result
    }

    private fun normalizePhone(raw: String): String? {
        var digits = raw.replace(Regex("[^0-9+]"), "")
        if (digits.startsWith("+")) digits = digits.substring(1)

        digits = when {
            digits.length == 11 && digits.startsWith("8") -> "7" + digits.substring(1)
            digits.length == 10 && digits.startsWith("9") -> "7$digits"
            else -> digits
        }

        return if (digits.length == 11 && digits.startsWith("7")) digits else null
    }
}
