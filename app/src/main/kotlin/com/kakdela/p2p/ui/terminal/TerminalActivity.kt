package com.kakdela.p2p.ui.terminal

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.kakdela.p2p.R
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import java.io.File

class TerminalActivity : AppCompatActivity() {

    private lateinit var mTerminalView: TerminalView
    private lateinit var mDrawerLayout: DrawerLayout
    private var mTerminalSession: TerminalSession? = null
    
    private val TAG = "TerminalActivity"

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Установка контента из предоставленного XML
        setContentView(R.layout.activity_termux)

        // Инициализация View по ID из вашего XML
        mTerminalView = findViewById(R.id.terminal_view)
        mDrawerLayout = findViewById(R.id.drawer_layout)
        
        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        val newSessionButton: MaterialButton = findViewById(R.id.new_session_button)
        val toggleKeyboardButton: MaterialButton = findViewById(R.id.toggle_keyboard_button)
        val sessionsListView: ListView = findViewById(R.id.terminal_sessions_list)

        // Базовая настройка TerminalView
        mTerminalView.apply {
            setTextSize(16)
            keepScreenOn = true
            requestFocus()
        }

        // Логика кнопок
        settingsButton.setOnClickListener {
            Log.d(TAG, "Settings clicked")
            // Здесь можно открыть настройки
        }

        newSessionButton.setOnClickListener {
            setupTerminalSession() // Создаем новую сессию
            mDrawerLayout.closeDrawer(GravityCompat.START)
        }

        toggleKeyboardButton.setOnClickListener {
            // В данной разметке это просто кнопка, логику можно расширить
            Log.d(TAG, "Toggle keyboard clicked")
        }

        setupTerminalSession()
    }

    private fun setupTerminalSession() {
        try {
            // Настройка путей окружения (как в оригинальном Termux)
            val termuxPrefix = File(filesDir, "usr")
            val homeDir = File(filesDir, "home")

            if (!termuxPrefix.exists()) termuxPrefix.mkdirs()
            if (!homeDir.exists()) homeDir.mkdirs()

            // Формируем переменные окружения
            val env = arrayOf(
                "PATH=${termuxPrefix.absolutePath}/bin:/system/bin:/system/xbin",
                "HOME=${homeDir.absolutePath}",
                "TERM=xterm-256color",
                "PREFIX=${termuxPrefix.absolutePath}",
                "TMPDIR=${termuxPrefix.absolutePath}/tmp"
            )

            // Определяем путь к шеллу
            val shellPath = when {
                File(termuxPrefix, "bin/bash").exists() -> "${termuxPrefix.absolutePath}/bin/bash"
                File(termuxPrefix, "bin/sh").exists() -> "${termuxPrefix.absolutePath}/bin/sh"
                else -> "/system/bin/sh"
            }

            val client = object : TerminalSessionClient {
                override fun onTextChanged(session: TerminalSession) {
                    mTerminalView.onScreenUpdated()
                }

                override fun onTitleChanged(session: TerminalSession) {
                    Log.d(TAG, "Title: ${session.title}")
                }

                override fun onSessionFinished(session: TerminalSession) {
                    if (!isFinishing) finish()
                }

                override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                    // Реализация копирования если нужно
                }

                override fun onPasteTextFromClipboard(session: TerminalSession?) {
                    // Реализация вставки если нужно
                }

                override fun onBell(session: TerminalSession) {}

                override fun onColorsChanged(session: TerminalSession) {}

                override fun onTerminalCursorStateChange(state: Boolean) {}

                override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
                    Log.d(TAG, "Shell PID: $pid")
                }

                override fun getTerminalCursorStyle(): Int = 0 

                override fun logError(tag: String?, message: String?) { Log.e(tag, message ?: "") }
                override fun logWarn(tag: String?, message: String?) { Log.w(tag, message ?: "") }
                override fun logInfo(tag: String?, message: String?) { Log.i(tag, message ?: "") }
                override fun logDebug(tag: String?, message: String?) { Log.d(tag, message ?: "") }
                override fun logVerbose(tag: String?, message: String?) { Log.v(tag, message ?: "") }
                
                override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
                    Log.e(tag, "${message ?: ""}: ${Log.getStackTraceString(e)}")
                }

                override fun logStackTrace(tag: String?, e: Exception?) {
                    Log.e(tag, Log.getStackTraceString(e))
                }
            }

            // Инициализация новой сессии
            // Параметры: [Путь к шеллу, Рабочая директория, Аргументы, Окружение, Лимит строк, Клиент]
            mTerminalSession = TerminalSession(
                shellPath,
                homeDir.absolutePath,
                arrayOf("-l"),
                env,
                10000, 
                client
            )

            // Привязываем сессию к View
            mTerminalView.attachSession(mTerminalSession)

        } catch (e: Exception) {
            Log.e(TAG, "Terminal setup failed", e)
        }
    }

    override fun onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        mTerminalView.onScreenUpdated()
    }

    override fun onDestroy() {
        try {
            mTerminalSession?.finishIfRunning()
        } catch (e: Exception) {
            Log.e(TAG, "Error finishing session", e)
        }
        super.onDestroy()
    }
}
