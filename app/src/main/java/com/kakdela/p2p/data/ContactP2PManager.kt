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
 * P2P Contact Discovery Manager.
 * Сопоставляет локальные контакты с DHT / Discovery сервером.
 */
class ContactP2PManager(
    private val context: Context,
    private val identityRepository: IdentityRepository
) {

    companion object {
        private const val TAG = "ContactP2PManager"
    }

    /**
     * Основной метод синхронизации контактов.
     */
    suspend fun syncContacts(): List<AppContact> = withContext(Dispatchers.IO) {

        // ---------- PERMISSION ----------
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_CONTACTS permission not granted")
            return@withContext emptyList()
        }

        // ---------- FETCH ONLINE USERS ----------
        val onlineUsers: List<UserPayload> = try {
            identityRepository.fetchAllNodesFromServer()
                .filter { !it.phone_hash.isNullOrBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Discovery fetch failed", e)
            emptyList()
        }

        // Map: phone_hash -> UserPayload
        val onlineMap: Map<String, UserPayload> =
            onlineUsers.associateBy { it.phone_hash!! }

        // ---------- READ LOCAL CONTACTS ----------
        val localContacts = fetchLocalContacts()

        // ---------- MATCHING ----------
        val mergedContacts = localContacts.map { contact ->
            val phoneHash =
                identityRepository.generatePhoneDiscoveryHash(contact.phoneNumber)

            val peer = onlineMap[phoneHash]

            if (peer != null) {
                contact.copy(
                    userHash = peer.hash,
                    publicKey = peer.publicKey,
                    lastKnownIp = peer.ip,
                    isOnline = true
                )
            } else {
                contact.copy(
                    userHash = null,
                    publicKey = null,
                    lastKnownIp = null,
                    isOnline = false
                )
            }
        }

        // ---------- SORT ----------
        mergedContacts.sortedWith(
            compareByDescending<AppContact> { it.isOnline }
                .thenByDescending { it.isRegistered }
                .thenBy { it.name.lowercase() }
        )
    }

    /**
     * Читает контакты из Android Contacts Provider.
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

                val nameIdx =
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    )
                val numberIdx =
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: "Unknown"
                    val rawNumber = cursor.getString(numberIdx) ?: continue

                    val normalized = normalizePhone(rawNumber) ?: continue

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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read contacts", e)
        }

        return result
    }

    /**
     * Строгая продакшн-нормализация номера.
     * Формат: 7XXXXXXXXXX
     */
    private fun normalizePhone(raw: String): String? {
        var digits = raw.replace(Regex("[^0-9+]"), "")

        if (digits.startsWith("+")) {
            digits = digits.substring(1)
        }

        digits = when {
            digits.length == 11 && digits.startsWith("8") ->
                "7" + digits.substring(1)

            digits.length == 10 && digits.startsWith("9") ->
                "7$digits"

            else -> digits
        }

        return if (digits.length == 11 && digits.startsWith("7")) {
            digits
        } else {
            null
        }
    }
}
