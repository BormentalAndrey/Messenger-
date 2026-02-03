package com.kakdela.p2p.ui.terminal

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.kakdela.p2p.R
import com.termux.app.TermuxInstaller
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

class TerminalActivity :
    AppCompatActivity(),
    TerminalSessionClient,
    TerminalViewClient {

    private lateinit var terminalView: TerminalView
    private lateinit var drawerLayout: DrawerLayout
    private var terminalSession: TerminalSession? = null

    companion object {
        private const val TAG = "TerminalActivity"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_termux)

        terminalView = findViewById(R.id.terminal_view)
        drawerLayout = findViewById(R.id.drawer_layout)

        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        val newSessionButton: MaterialButton = findViewById(R.id.new_session_button)
        val toggleKeyboardButton: MaterialButton = findViewById(R.id.toggle_keyboard_button)

        terminalView.setTerminalViewClient(this)
        terminalView.setTextSize(35)
        terminalView.keepScreenOn = true
        terminalView.setOnClickListener { showKeyboard() }

        settingsButton.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawer(GravityCompat.START)
            else
                drawerLayout.openDrawer(GravityCompat.START)
        }

        newSessionButton.setOnClickListener {
            setupSession()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        toggleKeyboardButton.setOnClickListener {
            toggleKeyboard()
        }

        // OnBackPressedDispatcher вместо устаревшего onBackPressed
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START))
                    drawerLayout.closeDrawer(GravityCompat.START)
                else
                    finish()
            }
        })

        // Запуск Termux bootstrap
        TermuxInstaller.setupBootstrapIfNeeded(this, Runnable {
            TermuxInstaller.setupStorageSymlinks(this)
            setupSession()
            terminalView.requestFocus()
            terminalView.postDelayed({ showKeyboard() }, 300)
        })
    }

    private fun setupSession() {
        terminalSession?.finishIfRunning()
        terminalSession = null

        try {
            // Используем свои директории, overridePrefixDir больше не существует
            val prefix = File(applicationContext.dataDir, "usr")
            val home = File(applicationContext.dataDir, "home")

            home.mkdirs()
            File(prefix, "tmp").mkdirs()

            val env = arrayOf(
                "TERM=xterm-256color",
                "HOME=${home.absolutePath}",
                "PREFIX=${prefix.absolutePath}",
                "PATH=${prefix.absolutePath}/bin:${prefix.absolutePath}/bin/applets",
                "LD_LIBRARY_PATH=${prefix.absolutePath}/lib",
                "LANG=en_US.UTF-8"
            )

            val shell = when {
                File(prefix, "bin/bash").canExecute() -> File(prefix, "bin/bash").absolutePath
                File(prefix, "bin/sh").canExecute() -> File(prefix, "bin/sh").absolutePath
                else -> "/system/bin/sh"
            }

            terminalSession = TerminalSession(
                shell,
                home.absolutePath,
                arrayOf("-l"),
                env,
                2000,
                this
            )

            terminalView.attachSession(terminalSession)
            terminalView.onScreenUpdated()

        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start terminal", t)
        }
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm.isAcceptingText)
            imm.hideSoftInputFromWindow(terminalView.windowToken, 0)
        else
            @Suppress("DEPRECATION")
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    // ────────────────────────────────
    // TerminalSessionClient
    // ────────────────────────────────

    override fun onTextChanged(session: TerminalSession) { terminalView.onScreenUpdated() }
    override fun onTitleChanged(session: TerminalSession) {}
    override fun onSessionFinished(session: TerminalSession) {}
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int = 0
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("termux", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.primaryClip?.getItemAt(0)?.text?.let {
            terminalSession?.write(it.toString())
        }
    }

    override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: TAG, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: TAG, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: TAG, message ?: "", e)
    }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: TAG, "stacktrace", e) }

    // ────────────────────────────────
    // TerminalViewClient
    // ────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = false

    override fun onSingleTapUp(event: MotionEvent) { showKeyboard() }
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun onScale(scale: Float): Float = scale
    override fun shouldBackButtonBeMappedToEscape(): Boolean = true
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true
    override fun isTerminalViewSelected(): Boolean =
        ::terminalView.isInitialized && terminalView.hasWindowFocus() && terminalView.isFocused

    override fun copyModeChanged(enabled: Boolean) { Log.d(TAG, "copyModeChanged: $enabled") }
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() { Log.d(TAG, "Terminal emulator set") }

    override fun onDestroy() {
        terminalSession?.finishIfRunning()
        super.onDestroy()
    }
    }
