package com.kakdela.p2p.data

import android.util.Log
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.api.WebViewApiClient
import com.kakdela.p2p.security.CryptoManager
import java.util.*

class UserManager(
    private val identityRepo: IdentityRepository
) {
    private val TAG = "UserManager"

    /**
     * Сохраняет пользователя исключительно в базу данных MySQL (InfinityFree)
     */
    suspend fun saveUserToFirestore(name: String, rawPhoneNumber: String) {
        // Очищаем номер: оставляем только цифры
        val cleanPhoneNumber = rawPhoneNumber.filter { it.isDigit() }

        // --- ЛОГИКА MYSQL (API.PHP) ---
        try {
            val myHash = identityRepo.getMyId() // Уникальный P2P ID (из CryptoManager)
            val myIp = identityRepo.getCurrentIp() // Текущий IP устройства
            val myPublicKey = CryptoManager.getMyPublicKeyStr() // Публичный ключ для шифрования

            val payload = UserPayload(
                hash = myHash,
                phone_hash = cleanPhoneNumber, // Используем номер как идентификатор
                publicKey = myPublicKey,
                ip = myIp,
                port = 8888, // Стандартный порт UDP
                phone = cleanPhoneNumber,
                lastSeen = System.currentTimeMillis()
            )

            // Отправляем данные на ваш api.php на хостинге InfinityFree
            val response = WebViewApiClient.announceSelf(payload)
            
            if (response.success) {
                Log.d(TAG, "Пользователь успешно зарегистрирован в MySQL (InfinityFree)")
            } else {
                Log.e(TAG, "Ошибка бэкенда: ${response.error}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка при синхронизации с MySQL", e)
        }
    }

    /**
     * Вспомогательный метод для получения статуса (заглушка, так как Firebase удален)
     */
    fun isUserLoggedIn(): Boolean {
        return identityRepo.getMyId().isNotEmpty()
    }
}
