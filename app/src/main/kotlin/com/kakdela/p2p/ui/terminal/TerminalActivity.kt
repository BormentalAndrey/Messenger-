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
import com.termux.app.TermuxInstaller
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

        // 1. Установка контента из XML
        setContentView(R.layout.activity_termux)

        // 2. Инициализация View
        mTerminalView = findViewById(R.id.terminal_view)
        mDrawerLayout = findViewById(R.id.drawer_layout)
        
        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        val newSessionButton: MaterialButton = findViewById(R.id.new_session_button)
        val toggleKeyboardButton: MaterialButton = findViewById(R.id.toggle_keyboard_button)
        val sessionsListView: ListView = findViewById(R.id.terminal_sessions_list)

        // 3. Базовая настройка View
        mTerminalView.apply {
            setTextSize(16)
            keepScreenOn = true
            requestFocus()
        }

        // 4. Логика кнопок
        settingsButton.setOnClickListener {
            Log.d(TAG, "Settings clicked")
        }

        newSessionButton.setOnClickListener {
            if (mTerminalSession != null) {
                setupTerminalSession() 
            }
            mDrawerLayout.closeDrawer(GravityCompat.START)
        }

        toggleKeyboardButton.setOnClickListener {
            mTerminalView.onCheckIsTextEditor() // Простейший триггер клавиатуры
        }

        // 5. КРИТИЧЕСКИЙ ШАГ: Сначала проверяем Bootstrap, затем запускаем сессию
        TermuxInstaller.setupBootstrapIfNeeded(this) {
            setupTerminalSession()
            // После установки bootstrap создаем симлинки на память
            TermuxInstaller.setupStorageSymlinks(this)
        }
    }

    private fun setupTerminalSession() {
        try {
            val termuxPrefix = File(filesDir, "usr")
            val homeDir = File(filesDir, "home")
            val tmpDir = File(termuxPrefix, "tmp")

            if (!termuxPrefix.exists()) termuxPrefix.mkdirs()
            if (!homeDir.exists()) homeDir.mkdirs()
            if (!tmpDir.exists()) tmpDir.mkdirs()

            // Переменные окружения для работы бинарников Termux
            val env = arrayOf(
                "PATH=${termuxPrefix.absolutePath}/bin",
                "LD_LIBRARY_PATH=${termuxPrefix.absolutePath}/lib",
                "HOME=${homeDir.absolutePath}",
                "TERM=xterm-256color",
                "PREFIX=${termuxPrefix.absolutePath}",
                "TMPDIR=${tmpDir.absolutePath}"
            )

            // Проверка наличия bash, иначе fallback на системный sh
            val shellPath = File(termuxPrefix, "bin/bash").let {
                if (it.exists()) it.absolutePath else "/system/bin/sh"
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
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Termux", text)
                    clipboard.setPrimaryClip(clip)
                }

                override fun onPasteTextFromClipboard(session: TerminalSession?) {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).coerceToText(this@TerminalActivity).toString()
                        mTerminalSession?.write(text)
                    }
                }

                override fun onBell(session: TerminalSession) {}
                override fun onColorsChanged(session: TerminalSession) {}
                override fun onTerminalCursorStateChange(state: Boolean) {}
                override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
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

            // Завершаем старую сессию перед созданием новой, если нужно
            mTerminalSession?.finishIfRunning()

            mTerminalSession = TerminalSession(
                shellPath,
                homeDir.absolutePath,
                arrayOf("-l"), // -l запускает shell как login shell
                env,
                10000, 
                client
            )

            mTerminalView.attachSession(mTerminalSession)

        } catch (e: Exception) {
            Log.e(TAG, "Terminal setup failed", e)
        }
    }

    override fun onResume() {
        super.onResume()
        mTerminalView.onScreenUpdated()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
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
