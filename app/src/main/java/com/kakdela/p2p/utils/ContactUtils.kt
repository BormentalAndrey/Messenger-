package com.kakdela.p2p.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.Phonenumber

/**
 * Composable helper для запроса разрешения на чтение контактов.
 */
@Composable
fun rememberContactsPermissionLauncher(
    onGranted: () -> Unit
): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onGranted()
    }

    return {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            onGranted()
        } else {
            launcher.launch(Manifest.permission.READ_CONTACTS)
        }
    }
}

/**
 * Утилита для работы с контактами.
 */
object ContactUtils {

    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    /**
     * Получает все телефонные номера из контактов и возвращает в стандартизированном E.164 формате.
     * Если номер не удалось распознать, он пропускается.
     */
    fun getNormalizedPhoneNumbers(context: Context, defaultRegion: String = "UA"): Set<String> {
        val numbers = mutableSetOf<String>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )

        cursor?.use {
            val idx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val raw = it.getString(idx) ?: continue
                val cleaned = raw.replace(Regex("[^0-9+]"), "") // оставляем + и цифры
                try {
                    val phoneNumber: Phonenumber.PhoneNumber =
                        phoneUtil.parse(cleaned, defaultRegion)
                    if (phoneUtil.isValidNumber(phoneNumber)) {
                        // E.164 формат, например: +380501234567
                        val formatted = phoneUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
                        numbers.add(formatted)
                    }
                } catch (e: NumberParseException) {
                    // Игнорируем некорректные номера
                }
            }
        }

        return numbers
    }
}
