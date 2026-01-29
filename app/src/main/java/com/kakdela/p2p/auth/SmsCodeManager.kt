package com.kakdela.p2p.auth

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import android.app.PendingIntent

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
     * @return true если отправка инициирована успешно или fallback открылся
     */
    fun sendCode(context: Context, phone: String, code: String): Boolean {
        // Проверка разрешения SEND_SMS
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.e(TAG, "SEND_SMS permission not granted")
            return false
        }

        // Проверка номера
        if (phone.isBlank()) {
            Log.e(TAG, "Phone number is empty")
            return false
        }

        val text = "Код подтверждения KakDela: $code"

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: run {
                Log.e(TAG, "SmsManager is null")
                return fallbackSend(context, phone, text)
            }

            // PendingIntent для отслеживания статуса отправки
            val sentIntent = Intent("SMS_SENT")
            val sentPI = PendingIntent.getBroadcast(
                context,
                0,
                sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Регистрируем BroadcastReceiver для отслеживания результата отправки
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    when (resultCode) {
                        android.app.Activity.RESULT_OK -> Log.d(TAG, "SMS SENT SUCCESS to $phone")
                        else -> {
                            Log.e(TAG, "SMS SENT FAILED to $phone with resultCode=$resultCode")
                            // Если не получилось, fallback
                            fallbackSend(context, phone, text)
                        }
                    }
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {}
                }
            }, IntentFilter("SMS_SENT"))

            // Отправка SMS напрямую
            smsManager.sendTextMessage(phone, null, text, sentPI, null)

            Log.d(TAG, "SMS отправлено (инициировано). phone=$phone code=$code")
            true
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException while sending SMS", se)
            fallbackSend(context, phone, text)
        } catch (iae: IllegalArgumentException) {
            Log.e(TAG, "Invalid phone number: $phone", iae)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while sending SMS", e)
            fallbackSend(context, phone, text)
        }
    }

    /**
     * Fallback: открывает стандартное приложение SMS с готовым текстом
     */
    private fun fallbackSend(context: Context, phone: String, text: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phone")
                putExtra("sms_body", text)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.w(TAG, "SMS fallback activated for $phone")
            true
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No SMS app found for fallback", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Fallback SMS failed", e)
            false
        }
    }
}
