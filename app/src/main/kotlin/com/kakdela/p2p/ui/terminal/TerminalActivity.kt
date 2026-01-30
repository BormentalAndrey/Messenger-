package com.kakdela.p2p.ui.terminal

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.kakdela.p2p.R
import com.termux.app.TermuxInstaller
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSession.SessionChangedCallback
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
        setContentView(R.layout.activity_termux)

        mTerminalView = findViewById(R.id.terminal_view)
        mDrawerLayout = findViewById(R.id.drawer_layout)

        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        val newSessionButton: MaterialButton = findViewById(R.id.new_session_button)
        val toggleKeyboardButton: MaterialButton = findViewById(R.id.toggle_keyboard_button)

        // Базовые настройки вида
        mTerminalView.apply {
            setTextSize(16)           // можно сделать настраиваемым
            keepScreenOn = true
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()

            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) showSoftKeyboard()
            }
        }

        // Кнопки
        settingsButton.setOnClickListener {
            mDrawerLayout.openDrawer(GravityCompat.START)
        }

        newSessionButton.setOnClickListener {
            setupTerminalSession()
            mDrawerLayout.closeDrawer(GravityCompat.START)
        }

        toggleKeyboardButton.setOnClickListener {
            showSoftKeyboard()
        }

        // Инициализация Termux bootstrap + запуск первой сессии
        TermuxInstaller.setupBootstrapIfNeeded(this) { success ->
            if (!success) {
                Log.e(TAG, "Bootstrap setup failed")
                // Здесь можно показать ошибку пользователю
                return@setupBootstrapIfNeeded
            }

            runOnUiThread {
                setupStorageSymlinks()
                setupTerminalSession()
            }
        }
    }

    private fun setupStorageSymlinks() {
        try {
            TermuxInstaller.setupStorageSymlinks(this)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to setup storage symlinks", e)
        }
    }

    private fun setupTerminalSession() {
        try {
            val termuxPrefix = File(filesDir, "usr")
            val homeDir = File(filesDir, "home")
            val tmpDir = File(termuxPrefix, "tmp")

            // Гарантируем наличие директорий
            termuxPrefix.mkdirs()
            homeDir.mkdirs()
            tmpDir.mkdirs()

            val env = arrayOf(
                "PATH=\( {termuxPrefix.absolutePath}/bin: \){termuxPrefix.absolutePath}/local/bin",
                "LD_LIBRARY_PATH=${termuxPrefix.absolutePath}/lib",
                "HOME=${homeDir.absolutePath}",
                "TERM=xterm-256color",
                "PREFIX=${termuxPrefix.absolutePath}",
                "TMPDIR=${tmpDir.absolutePath}",
                "TZ=${java.util.TimeZone.getDefault().id}",
                "ANDROID_DATA=${android.os.Environment.getDataDirectory().absolutePath}",
                "ANDROID_ROOT=${android.os.Environment.getRootDirectory().absolutePath}"
            )

            // Выбор шелла (приоритет bash → sh → системный sh)
            val shellPath = when {
                File(termuxPrefix, "bin/bash").exists() -> "${termuxPrefix.absolutePath}/bin/bash"
                File(termuxPrefix, "bin/sh").exists() -> "${termuxPrefix.absolutePath}/bin/sh"
                else -> "/system/bin/sh"
            }

            val client = object : SessionChangedCallback {
                override fun onTextChanged(session: TerminalSession) {
                    mTerminalView.onScreenUpdated()
                }

                override fun onTitleChanged(session: TerminalSession) {
                    runOnUiThread { title = session.title ?: "Terminal" }
                }

                override fun onSessionFinished(session: TerminalSession) {
                    if (!isFinishing && !isChangingConfigurations) {
                        finish()
                    }
                }

                override fun onClipboardText(session: TerminalSession, text: String) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Terminal", text)
                    clipboard.setPrimaryClip(clip)
                }

                override fun onPasteFromClipboard(session: TerminalSession?) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    if (!clipboard.hasPrimaryClip()) return

                    val clip = clipboard.primaryClip ?: return
                    if (clip.itemCount == 0) return

                    val text = clip.getItemAt(0).coerceToText(this@TerminalActivity)?.toString() ?: return
                    mTerminalSession?.write(text)
                }

                override fun onBell(session: TerminalSession) {
                    // Можно добавить вибрацию или звук
                }

                override fun onColorsChanged(session: TerminalSession) {}
                override fun onTerminalCursorStateChange(state: Boolean) {}
                override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

                override fun logError(tag: String?, message: String?) { Log.e(tag, message ?: "") }
                override fun logWarn(tag: String?, message: String?) { Log.w(tag, message ?: "") }
                override fun logInfo(tag: String?, message: String?) { Log.i(tag, message ?: "") }
                override fun logDebug(tag: String?, message: String?) { Log.d(tag, message ?: "") }
                override fun logVerbose(tag: String?, message: String?) { Log.v(tag, message ?: "") }
                override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
                    Log.e(tag, "${message ?: ""}", e)
                }
                override fun logStackTrace(tag: String?, e: Exception?) {
                    Log.e(tag, "Stack trace", e)
                }
            }

            // Завершаем старую сессию, если была
            mTerminalSession?.finishIfRunning()

            mTerminalSession = TerminalSession(
                shellPath,
                homeDir.absolutePath,
                arrayOf("-l"),           // login shell → загружает .profile / .bashrc
                env,
                10000,                   // max lines в scrollback
                client
            ).also { session ->
                mTerminalView.attachSession(session)

                // Очень важно: сразу после attachSession обновляем размер
                updateTerminalSize()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create terminal session", e)
            // Можно показать тост / диалог с ошибкой
        }
    }

    private fun updateTerminalSize() {
        mTerminalSession?.let { session ->
            val cols = mTerminalView.getColumns()
            val rows = mTerminalView.getRows()

            if (cols > 0 && rows > 0) {
                session.resize(cols, rows)
                Log.d(TAG, "Terminal resized to \( {cols}x \){rows}")
            }
        }
    }

    private fun showSoftKeyboard() {
        mTerminalView.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(mTerminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    // Важно для поворота экрана / изменения конфигурации
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateTerminalSize()
    }

    override fun onResume() {
        super.onResume()
        mTerminalView.onScreenUpdated()
        updateTerminalSize()
        showSoftKeyboard()           // удобно — клавиатура появляется автоматически
    }

    override fun onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        mTerminalSession?.finishIfRunning()
        super.onDestroy()
    }
}
