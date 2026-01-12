package com.kakdela.p2p.network

import android.content.Context
import android.util.Log
import android.webkit.CookieManager

object CookieStore {
    private const val PREF_NAME = "p2p_network_cookies"
    private const val KEY_TEST_COOKIE = "__test_cookie"
    private const val TAG = "CookieStore"

    var testCookie: String? = null
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        testCookie = prefs.getString(KEY_TEST_COOKIE, null)
        Log.d(TAG, "CookieStore инициализирован. Кука: $testCookie")
    }

    fun updateFromWebView(context: Context, url: String) {
        val rawCookie = CookieManager.getInstance().getCookie(url)
        if (rawCookie != null) {
            val parts = rawCookie.split(";")
            val found = parts.find { it.trim().startsWith("__test=") }?.trim()
            if (found != null) {
                testCookie = found
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TEST_COOKIE, found)
                    .apply()
                Log.d(TAG, "Кука успешно обновлена: $found")
            }
        }
    }

    fun clear(context: Context) {
        testCookie = null
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TEST_COOKIE)
            .apply()
    }
}
