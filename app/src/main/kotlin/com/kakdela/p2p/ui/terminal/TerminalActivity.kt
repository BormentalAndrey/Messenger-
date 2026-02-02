package com.kakdela.p2p.ui.terminal

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
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

class TerminalActivity : AppCompatActivity(), TerminalSessionClient {

    private lateinit var mTerminalView: TerminalView
    private lateinit var mDrawerLayout: DrawerLayout
    private var mTerminalSession: TerminalSession? = null

    companion object {
        private const val TAG = "TerminalActivity"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Предотвращаем отключение экрана пока терминал активен
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_termux)

        // Инициализация UI
        mTerminalView = findViewById(R.id.terminal_view)
        mDrawerLayout = findViewById(R.id.drawer_layout)
        
        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        val newSessionButton: MaterialButton = findViewById(R.id.new_session_button)
        val toggleKeyboardButton: MaterialButton = findViewById(R.id.toggle_keyboard_button)

        // Настройка TerminalView
        mTerminalView.apply {
            setTextSize(35) // Размер шрифта
            keepScreenOn = true
            requestFocus()
            setOnClickListener { showSoftKeyboard() }
        }

        // Обработчики кнопок
        settingsButton.setOnClickListener {
            if (!mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.openDrawer(GravityCompat.START)
            } else {
                mDrawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        newSessionButton.setOnClickListener {
            setupTerminalSession()
            mDrawerLayout.closeDrawer(GravityCompat.START)
        }

        toggleKeyboardButton.setOnClickListener {
            toggleSoftKeyboard()
        }

        // ЗАПУСК: Проверка установки и старт
        TermuxInstaller.setupBootstrapIfNeeded(this) {
            // Этот код выполнится только после успешной загрузки и распаковки
            
            // Настройка симлинков (storage) в фоне
            TermuxInstaller.setupStorageSymlinks(this)
            
            // Запуск сессии в UI потоке
            runOnUiThread {
                setupTerminalSession()
            }
        }
    }

    private fun setupTerminalSession() {
        // Очищаем старую сессию
        mTerminalSession?.finishIfRunning()

        try {
            // 1. Определение путей (соответствует путям установщика)
            val filesDir = this.filesDir.absolutePath
            val termuxPrefix = "$filesDir/usr"
            val homeDir = "$filesDir/home"
            val tmpDir = "$termuxPrefix/tmp"
            val appletsDir = "$termuxPrefix/bin/applets"
            
            // Гарантируем существование папок
            File(homeDir).mkdirs()
            File(tmpDir).mkdirs()

            // 2. Переменные окружения (Linux Environment)
            val env = arrayOf(
                "TERM=xterm-256color",
                "HOME=$homeDir",
                "PREFIX=$termuxPrefix",
                "TMPDIR=$tmpDir",
                "PATH=$termuxPrefix/bin:$appletsDir", 
                "LD_LIBRARY_PATH=$termuxPrefix/lib",
                "LANG=en_US.UTF-8",
                "ANDROID_ROOT=${System.getenv("ANDROID_ROOT") ?: "/system"}",
                "ANDROID_DATA=${System.getenv("ANDROID_DATA") ?: "/data"}"
            )

            // 3. Выбор оболочки (Shell). Проверяем, есть ли bash после установки.
            val shellPath = when {
                File("$termuxPrefix/bin/bash").canExecute() -> "$termuxPrefix/bin/bash"
                File("$termuxPrefix/bin/sh").canExecute() -> "$termuxPrefix/bin/sh"
                else -> "/system/bin/sh" // Fallback если установка не удалась
            }

            Log.i(TAG, "Starting session with shell: $shellPath")

            // 4. Создание сессии
            val session = TerminalSession(
                shellPath,
                homeDir,
                arrayOf("-l"), // login shell (читает .bashrc)
                env,
                2000, 
                this 
            )

            mTerminalSession = session
            mTerminalView.attachSession(session)
            mTerminalView.onScreenUpdated()
            
            // Фокус и клавиатура с небольшой задержкой
            mTerminalView.postDelayed({
                showSoftKeyboard()
            }, 500)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup terminal session", e)
        }
    }

    private fun showSoftKeyboard() {
        mTerminalView.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(mTerminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun toggleSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    // --- TerminalSessionClient Implementation ---

    override fun onTextChanged(session: TerminalSession) {
        mTerminalView.onScreenUpdated()
    }

    override fun onTitleChanged(session: TerminalSession) {}

    override fun onSessionFinished(session: TerminalSession) {
        if (session == mTerminalSession && !isFinishing) {
           // Можно закрыть активити: finish()
        }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Termux", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this).toString()
            mTerminalSession?.write(text)
        }
    }

    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int = 0 
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    // --- Logging ---
    override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: TAG, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: TAG, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag ?: TAG, message ?: "", e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: TAG, "Stack trace", e) }

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
        super.onDestroy()
        mTerminalSession?.finishIfRunning()
    }
}
