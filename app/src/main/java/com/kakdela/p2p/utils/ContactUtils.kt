package com.kakdela.p2p.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.Phonenumber

/**
 * Composable хелпер для запроса разрешения на контакты.
 * Теперь находится в единственном экземпляре здесь.
 */
@Composable
fun rememberContactsPermissionLauncher(
    onGranted: () -> Unit
): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onGranted()
        } else {
            Log.w("ContactUtils", "READ_CONTACTS permission denied by user")
        }
    }

    return {
        val currentStatus = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        )
        
        if (currentStatus == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            launcher.launch(Manifest.permission.READ_CONTACTS)
        }
    }
}

/**
 * Утилита для работы с контактами и нормализации номеров.
 */
object ContactUtils {

    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()
    private const val TAG = "ContactUtils"

    /**
     * Получает все телефонные номера из книги и возвращает их в формате E.164.
     * Используется для сопоставления локальных контактов с P2P узлами.
     */
    fun getNormalizedPhoneNumbers(context: Context, defaultRegion: String = "UA"): Set<String> {
        val numbers = mutableSetOf<String>()
        
        // Проверка разрешения перед запросом (защита от краша)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing READ_CONTACTS permission!")
            return emptySet()
        }

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                
                while (cursor.moveToNext()) {
                    val rawNumber = cursor.getString(numberIndex) ?: continue
                    
                    // Нормализация через Google libphonenumber
                    try {
                        val parsedNumber: Phonenumber.PhoneNumber = phoneUtil.parse(rawNumber, defaultRegion)
                        if (phoneUtil.isValidNumber(parsedNumber)) {
                            val formatted = phoneUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
                            numbers.add(formatted)
                        }
                    } catch (e: NumberParseException) {
                        // Если это не номер (например, короткий код), просто скипаем
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts: ${e.message}")
        }

        Log.d(TAG, "Found ${numbers.size} valid normalized contacts")
        return numbers
    }

    /**
     * Утилитный метод для очистки номера до 10 цифр (для упрощенного поиска)
     */
    fun cleanToLastTen(phone: String): String {
        return phone.replace(Regex("[^0-9]"), "").takeLast(10)
    }
}
