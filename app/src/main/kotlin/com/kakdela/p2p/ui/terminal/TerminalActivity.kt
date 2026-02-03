package com.kakdela.p2p.ui.terminal

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
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
import com.termux.shared.termux.TermuxConstants
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class TerminalActivity :
    AppCompatActivity(),
    TerminalSessionClient,
    TerminalViewClient {

    private lateinit var terminalView: TerminalView
    private lateinit var drawerLayout: DrawerLayout
    private var terminalSession: TerminalSession? = null

    companion object {
        private const val TAG = "TerminalActivity"

        // GitHub Termux bootstrap release
        private const val GITHUB_BASE_URL =
            "https://github.com/termux/termux-packages/releases/download/bootstrap"

        private val ARCH_BOOTSTRAPS = mapOf(
            "armeabi-v7a" to "bootstrap-aarch32.tar.gz",
            "arm64-v8a" to "bootstrap-aarch64.tar.gz",
            "x86" to "bootstrap-x86_32.tar.gz",
            "x86_64" to "bootstrap-x86_64.tar.gz"
        )
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START))
                    drawerLayout.closeDrawer(GravityCompat.START)
                else
                    finish()
            }
        })

        // Выбор: скачивать bootstrap или нет
        AlertDialog.Builder(this)
            .setTitle("Установка Termux")
            .setMessage("Скачать системные компоненты Termux (bootstrap)?")
            .setPositiveButton("Да") { _, _ ->
                val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
                val archiveName = ARCH_BOOTSTRAPS[arch] ?: ARCH_BOOTSTRAPS["arm64-v8a"]!!
                val url = "$GITHUB_BASE_URL/$archiveName"
                Log.i(TAG, "Selected architecture: $arch, bootstrap URL: $url")

                downloadAndSetupBootstrap(url) {
                    setupStorageSymlinks()
                    setupSession()
                    terminalView.requestFocus()
                    terminalView.postDelayed({ showKeyboard() }, 300)
                }
            }
            .setNegativeButton("Нет") { _, _ ->
                setupStorageSymlinks()
                setupSession()
                terminalView.requestFocus()
                terminalView.postDelayed({ showKeyboard() }, 300)
            }
            .setCancelable(false)
            .show()
    }

    // ---------------------------------------------------------
    // Скачивание и установка bootstrap
    // ---------------------------------------------------------
    private fun downloadAndSetupBootstrap(url: String, onFinished: () -> Unit) {
        Thread {
            try {
                val tmpDir = File(TermuxConstants.TERMUX_PREFIX_DIR, "tmp")
                if (!tmpDir.exists()) tmpDir.mkdirs()

                val archiveFile = File(tmpDir, "bootstrap.tar.gz")
                downloadBootstrapArchive(url, archiveFile)
                extractBootstrapArchive(archiveFile)
                onFinished()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download/setup bootstrap", e)
            }
        }.start()
    }

    private fun downloadBootstrapArchive(url: String, destFile: File) {
        try {
            if (destFile.exists()) destFile.delete()

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            connection.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Bootstrap archive downloaded to ${destFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download bootstrap archive", e)
        }
    }

    private fun extractBootstrapArchive(archiveFile: File) {
        try {
            val prefix = TermuxConstants.TERMUX_PREFIX_DIR
            GZIPInputStream(archiveFile.inputStream()).use { gis ->
                TarArchiveInputStream(gis).use { tar ->
                    var entry = tar.nextTarEntry
                    while (entry != null) {
                        val outFile = File(prefix, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out ->
                                tar.copyTo(out)
                            }
                        }
                        entry = tar.nextTarEntry
                    }
                }
            }
            Log.i(TAG, "Bootstrap archive extracted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract bootstrap archive", e)
        }
    }

    // ---------------------------------------------------------
    // Storage symlinks
    // ---------------------------------------------------------
    private fun setupStorageSymlinks() {
        try {
            val storageDir = File(TermuxConstants.TERMUX_HOME_DIR, "storage")
            if (!storageDir.exists()) storageDir.mkdirs()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup storage symlinks", e)
        }
    }

    // ---------------------------------------------------------
    // Настройка сессии терминала
    // ---------------------------------------------------------
    private fun setupSession() {
        terminalSession?.finishIfRunning()
        terminalSession = null

        try {
            val prefix: File = TermuxConstants.TERMUX_PREFIX_DIR
            val home: File = TermuxConstants.TERMUX_HOME_DIR

            if (!home.exists()) home.mkdirs()

            val env = arrayOf(
                "TERM=xterm-256color",
                "HOME=${home.absolutePath}",
                "PREFIX=${prefix.absolutePath}",
                "PATH=${prefix.absolutePath}/bin:${prefix.absolutePath}/bin/applets",
                "LD_LIBRARY_PATH=${prefix.absolutePath}/lib",
                "LANG=en_US.UTF-8"
            )

            val bash = File(prefix, "bin/bash")
            val sh = File(prefix, "bin/sh")

            val shell = when {
                bash.exists() && bash.canExecute() -> bash.absolutePath
                sh.exists() && sh.canExecute() -> sh.absolutePath
                else -> "/system/bin/sh"
            }

            Log.i(TAG, "Starting shell: $shell")
            Log.i(TAG, "PREFIX=$prefix")
            Log.i(TAG, "HOME=$home")

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

    // ---------------------------------------------------------
    // Клавиатура
    // ---------------------------------------------------------
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

    // ───────── TerminalSessionClient ─────────
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
        cb.primaryClip?.getItemAt(0)?.text?.let { terminalSession?.write(it.toString()) }
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

    // ───────── TerminalViewClient ─────────
    override fun onKeyDown(keyCode: Int, event: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, event: KeyEvent, session: TerminalSession): Boolean = false
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
