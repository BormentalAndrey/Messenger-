package com.kakdela.p2p.network

object CookieStore {
    var testCookie: String? = null

    fun updateCookie(rawCookie: String) {
        // Вырезаем именно значение __test=...
        val parts = rawCookie.split(";")
        testCookie = parts.find { it.trim().startsWith("__test=") }
    }
}
