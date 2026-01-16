package com.kakdela.p2p.api

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class WebkitCookieJar : CookieJar {
    private val cookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        for (cookie in cookies) {
            cookieManager.setCookie(urlString, cookie.toString())
        }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val urlString = url.toString()
        val cookieHeader = cookieManager.getCookie(urlString) ?: return emptyList()

        val cookies = mutableListOf<Cookie>()
        // Разбиваем строку кук (key=value; key2=value2) на отдельные объекты
        val splitCookies = cookieHeader.split(";")
        for (cookieStr in splitCookies) {
            Cookie.parse(url, cookieStr.trim())?.let { cookies.add(it) }
        }
        return cookies
    }
}
