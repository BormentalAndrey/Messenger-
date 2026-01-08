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
 *
 * 🔐 Приватность:
 *  – в сеть отправляется ТОЛЬКО SHA-256 хеш номера
 *  – номер телефона никогда не покидает устройство
 *
 * 📡 Сеть:
 *  – UDP + DHT-поиск
 *  – multicast listeners (без перетирания)
 */
class ContactP2PManager(
    private val context: Context,
    private val identityRepo: IdentityRepository
) {

    /**
     * phoneHash -> "publicKey|ip"
     */
    private val discoveryResults = ConcurrentHashMap<String, String>()

    /**
     * Основной метод синхронизации контактов
     */
    suspend fun syncContacts(): List<AppContact> = withContext(Dispatchers.IO) {

        /* ===================== PERMISSION ===================== */

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("P2P_CONTACTS", "READ_CONTACTS permission not granted")
            return@withContext emptyList()
        }

        /* ===================== LOAD CONTACTS ===================== */

        val localContacts = fetchLocalPhoneContacts()
        if (localContacts.isEmpty()) return@withContext emptyList()

        discoveryResults.clear()

        /* ===================== LISTENER ===================== */

        val listener = listener@{ type: String, data: String, fromIp: String ->

            if (type != "STORE_RESPONSE") return@listener

            // Ожидаемый формат: "<phoneHash>:<publicKey>"
            val parts = data.split(":", limit = 2)
            if (parts.size != 2) return@listener

            val phoneHash = parts[0]
            val publicKey = parts[1]

            discoveryResults[phoneHash] = "$publicKey|$fromIp"

            Log.d(
                "P2P_CONTACTS",
                "Found peer hash=$phoneHash ip=$fromIp"
            )
        }

        // НЕ перетираем другие слушатели
        identityRepo.addListener(listener)

        try {
            /* ===================== DHT SEARCH ===================== */

            localContacts.forEach { contact ->
                val hash = sha256(contact.phoneNumber)
                identityRepo.findPeerInDHT(hash)
            }

            /* ===================== WAIT ===================== */

            // UDP асинхронный — ждём ответы
            delay(2500)

        } finally {
            identityRepo.removeListener(listener)
        }

        /* ===================== MERGE ===================== */

        val merged = localContacts.map { contact ->
            val hash = sha256(contact.phoneNumber)
            val found = discoveryResults[hash]

            if (found != null) {
                val parts = found.split("|", limit = 2)
                val pubKey = parts[0]
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

        /* ===================== SORT ===================== */

        return@withContext merged.sortedWith(
            compareByDescending<AppContact> { it.isRegistered }
                .thenBy { it.name.lowercase() }
        )
    }

    /* ======================================================= */
    /* ===================== CONTACTS ======================== */
    /* ======================================================= */

    private fun fetchLocalPhoneContacts(): List<AppContact> {
        val contacts = mutableListOf<AppContact>()
        val seenPhones = HashSet<String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )

        cursor?.use {
            val nameIdx =
                it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx =
                it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: "Unknown"
                val rawPhone = it.getString(phoneIdx) ?: continue
                val phone = normalizePhone(rawPhone) ?: continue

                if (seenPhones.add(phone)) {
                    contacts += AppContact(
                        name = name,
                        phoneNumber = phone
                    )
                }
            }
        }

        return contacts
    }

    /**
     * Минимальная нормализация номера
     * 8 (999) 123-45-67 → 79991234567
     */
    private fun normalizePhone(raw: String): String? {
        var phone = raw.replace(Regex("[^0-9]"), "")
        if (phone.isEmpty()) return null

        if (phone.length == 11 && phone.startsWith("8")) {
            phone = "7" + phone.substring(1)
        } else if (phone.length == 10) {
            phone = "7$phone"
        }

        return if (phone.length >= 10) phone else null
    }

    /**
     * SHA-256 хеш номера
     */
    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
