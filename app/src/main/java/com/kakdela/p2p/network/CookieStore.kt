package com.kakdela.p2p.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Хранилище сессионных кук для обхода защиты InfinityFree.
 * Сохраняет куку в памяти и дублирует в SharedPreferences для выживания при перезапуске.
 */
object CookieStore {
    private const val PREF_NAME = "p2p_network_cookies"
    private const val KEY_TEST_COOKIE = "__test_cookie"
    private val TAG = "CookieStore"

    // Переменная в памяти для быстрого доступа Interceptor-ом
    var testCookie: String? = null
        private set

    /**
     * Инициализация при старте приложения (вызывать в MyApplication)
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        testCookie = prefs.getString(KEY_TEST_COOKIE, null)
        Log.d(TAG, "CookieStore инициализирован. Текущая кука: $testCookie")
    }

    /**
     * Парсит строку кук из WebView и сохраняет актуальную
     */
    fun updateCookie(context: Context, rawCookie: String) {
        // Ищем фрагмент, начинающийся с __test=
        val parts = rawCookie.split(";")
        val foundCookie = parts.find { it.trim().startsWith("__test=") }?.trim()

        if (foundCookie != null) {
            testCookie = foundCookie
            
            // Сохраняем в постоянную память
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_TEST_COOKIE, foundCookie).apply()
            
            Log.d(TAG, "Кука обновлена и сохранена: $foundCookie")
        }
    }

    /**
     * Очистка куки (если она протухла)
     */
    fun clearCookie(context: Context) {
        testCookie = null
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_TEST_COOKIE).apply()
    }
}
