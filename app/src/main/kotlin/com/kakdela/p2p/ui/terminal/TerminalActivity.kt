package com.kakdela.p2p.ui.terminal

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class TerminalActivity : AppCompatActivity() {

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Простой контейнер
        val root = FrameLayout(this)
        root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Сообщение пользователю, что терминал недоступен (поскольку Termux library удалена)
        val messageView = TextView(this).apply {
            text = "Terminal feature is disabled\n(Termux library was removed from the project)"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        root.addView(messageView)
        setContentView(root)

        // Подготовка директорий (оставлена для совместимости, если в будущем вернётся Termux)
        val homeDir = File(filesDir, "home").apply { if (!exists()) mkdirs() }
        val usrBin = File(filesDir, "usr/bin").apply { if (!exists()) mkdirs() }
        val tmpDir = File(cacheDir, "tmp").apply { if (!exists()) mkdirs() }

        // Окружение (оставлено для возможного будущего использования)
        val env = arrayOf(
            "HOME=${homeDir.absolutePath}",
            "PWD=${homeDir.absolutePath}",
            "TMPDIR=${tmpDir.absolutePath}",
            "PATH=/system/bin:/system/xbin:${usrBin.absolutePath}",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LD_LIBRARY_PATH=/system/lib64:/system/lib"
        )

        // Логируем, что терминал отключён
        Log.w("Terminal", "TerminalActivity started, but terminal functionality is disabled (Termux library removed)")
    }

    override fun onResume() {
        super.onResume()
        // Фокус на корневом view (не обязателен, но оставлен)
        window.decorView.requestFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("Terminal", "TerminalActivity destroyed")
    }

    // Логирование (оставлены методы из оригинального кода для совместимости)
    fun logError(tag: String?, message: String?) { Log.e(tag ?: "Terminal", message ?: "") }
    fun logWarn(tag: String?, message: String?) { Log.w(tag ?: "Terminal", message ?: "") }
    fun logInfo(tag: String?, message: String?) { Log.i(tag ?: "Terminal", message ?: "") }
    fun logDebug(tag: String?, message: String?) { Log.d(tag ?: "Terminal", message ?: "") }
    fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: "Terminal", message ?: "") }
    fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag ?: "Terminal", message ?: "", e) }
    fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: "Terminal", "stacktrace", e) }
}
