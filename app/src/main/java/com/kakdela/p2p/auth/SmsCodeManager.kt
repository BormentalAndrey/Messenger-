package com.kakdela.p2p.auth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

object SmsCodeManager {

    private const val TAG = "SmsCodeManager"

    /**
     * Генерация 6-значного OTP кода
     */
    fun generateCode(): String {
        return (100000..999999).random().toString()
    }

    /**
     * Отправка SMS с кодом подтверждения
     *
     * @return true если отправка инициирована успешно
     */
    fun sendCode(context: Context, phone: String, code: String): Boolean {
        // 1. Проверка runtime-permission SEND_SMS
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.e(TAG, "SEND_SMS permission not granted")
            return false
        }

        // 2. Проверка номера
        if (phone.isBlank()) {
            Log.e(TAG, "Phone number is empty")
            return false
        }

        val text = "Код подтверждения KakDela: $code"

        return try {
            /**
             * КРИТИЧЕСКИ ВАЖНО:
             * Используем getDefault(), потому что:
             * - он корректно выбирает активную SIM
             * - он РЕАЛЬНО отправляет SMS
             * - он пишет SMS в «Исходящие»
             * - он уже работает у тебя в SmsChatManager
             */
            val smsManager = SmsManager.getDefault()

            smsManager.sendTextMessage(
                phone,
                null,
                text,
                null,
                null
            )

            Log.d(TAG, "SMS отправлено. phone=$phone code=$code")
            true
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException while sending SMS", se)
            false
        } catch (iae: IllegalArgumentException) {
            Log.e(TAG, "Invalid phone number: $phone", iae)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while sending SMS", e)
            false
        }
    }
}
