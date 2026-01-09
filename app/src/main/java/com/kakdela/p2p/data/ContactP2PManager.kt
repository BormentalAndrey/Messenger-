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

class ContactP2PManager(
    private val context: Context,
    private val identityRepo: IdentityRepository
) {
    companion object { private const val TAG = "ContactP2PManager" }

    private val discoveryResults = ConcurrentHashMap<String, String>() // phoneHash -> "publicKey|ip"

    suspend fun syncContacts(): List<AppContact> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }

        val localContacts = fetchLocalPhoneContacts()
        if (localContacts.isEmpty()) return@withContext emptyList()

        // Очищаем старые результаты перед новым поиском
        discoveryResults.clear()

        // Поиск в DHT и на сервере для каждого контакта
        localContacts.forEach { contact ->
            val phoneHash = sha256(contact.phoneNumber)
            // Запускаем асинхронный поиск через репозиторий
            val peerDeferred = identityRepo.findPeerInDHT(phoneHash)
            
            // Если данные нашлись сразу (в кэше или на сервере)
            val peer = peerDeferred.await()
            if (peer != null) {
                discoveryResults[phoneHash] = "${peer.publicKey}|${peer.ip ?: ""}"
            }
        }

        // Небольшая задержка для UDP ответов от соседей
        delay(1000)

        val merged = localContacts.map { contact ->
            val hash = sha256(contact.phoneNumber)
            val foundData = discoveryResults[hash]

            if (foundData != null) {
                val parts = foundData.split("|", limit = 2)
                contact.copy(
                    isRegistered = true,
                    publicKey = parts.getOrNull(0).orEmpty(),
                    lastKnownIp = parts.getOrNull(1).orEmpty()
                )
            } else {
                contact
            }
        }

        return@withContext merged.sortedWith(
            compareByDescending<AppContact> { it.isRegistered }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun fetchLocalPhoneContacts(): List<AppContact> {
        val contacts = mutableListOf<AppContact>()
        val seenPhones = HashSet<String>()
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)

        try {
            context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { cursor ->
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
        } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}") }
        return contacts
    }

    private fun normalizePhone(raw: String): String? {
        var phone = raw.replace(Regex("[^0-9]"), "")
        if (phone.isEmpty()) return null
        if (phone.length == 11 && phone.startsWith("8")) phone = "7" + phone.substring(1)
        return if (phone.length >= 10) phone else null
    }

    private fun sha256(input: String): String = MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}
