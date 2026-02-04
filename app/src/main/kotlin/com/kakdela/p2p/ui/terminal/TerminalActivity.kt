package com.kakdela.p2p.ui.terminal

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.system.Os
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
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class TerminalActivity :
    AppCompatActivity(),
    TerminalSessionClient,
    TerminalViewClient {

    private lateinit var terminalView: TerminalView
    private lateinit var drawerLayout: DrawerLayout
    private var terminalSession: TerminalSession? = null

    companion object {
        private const val TAG = "TerminalActivity"

        private const val BOOTSTRAP_TAG = "bootstrap-2026.02.01-r1+apt.android-7"

        private const val BOOTSTRAP_AARCH64 = "bootstrap-aarch64.zip"
        private const val BOOTSTRAP_ARM = "bootstrap-arm.zip"
        private const val BOOTSTRAP_I686 = "bootstrap-i686.zip"
        private const val BOOTSTRAP_X86_64 = "bootstrap-x86_64.zip"
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

        settingsButton.setOnClickListener { Log.d(TAG, "Settings button clicked") }

        newSessionButton.setOnClickListener {
            setupSession()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        toggleKeyboardButton.setOnClickListener { toggleKeyboard() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else finish()
            }
        })

        checkAndInstallBootstrap()
    }

    private fun showErrorAndExit(message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Ошибка")
                .setMessage(message)
                .setPositiveButton("Закрыть") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    private fun checkAndInstallBootstrap() {
        val prefixDir = File(filesDir, "usr")
        val bashFile = File(prefixDir, "bin/bash")

        if (bashFile.exists() && bashFile.canExecute()) {
            setupSession()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Установка системы")
            .setMessage("Необходимо загрузить базовые компоненты (~30 МБ). Продолжить?")
            .setPositiveButton("Да") { _, _ ->
                val archiveName = detectBootstrapArchiveName()
                if (archiveName == null) {
                    showErrorAndExit("Архитектура устройства не поддерживается")
                    return@setPositiveButton
                }

                val encodedTag = BOOTSTRAP_TAG.replace("+", "%2B")
                val downloadUrl =
                    "https://github.com/termux/termux-packages/releases/download/$encodedTag/$archiveName"

                startBootstrapDownload(downloadUrl, archiveName)
            }
            .setNegativeButton("Отмена") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun detectBootstrapArchiveName(): String? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        return when {
            abi.contains("arm64") || abi.contains("aarch64") -> BOOTSTRAP_AARCH64
            abi.contains("x86_64") -> BOOTSTRAP_X86_64
            abi.contains("x86") || abi.contains("i686") -> BOOTSTRAP_I686
            abi.contains("armeabi") || abi.contains("arm") -> BOOTSTRAP_ARM
            else -> null
        }
    }

    private fun startBootstrapDownload(urlStr: String, archiveName: String) {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Загрузка Termux")
            setMessage("Подключение к серверу...")
            setIndeterminate(true)
            setCancelable(false)
            show()
        }

        Thread {
            try {
                val tmpDir = File(filesDir, "tmp").apply { mkdirs() }
                val zipFile = File(tmpDir, archiveName)

                runOnUiThread { progressDialog.setMessage("Загрузка архива...") }
                downloadFile(urlStr, zipFile)

                runOnUiThread { progressDialog.setMessage("Распаковка и настройка...") }
                unzipBootstrap(zipFile)
                applySymlinks()

                zipFile.delete()

                runOnUiThread {
                    progressDialog.dismiss()
                    setupSession()
                    showKeyboard()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bootstrap error", e)
                runOnUiThread {
                    progressDialog.dismiss()
                    val msg = if (e.message?.contains("404") == true)
                        "Файл не найден (404). Проверьте BOOTSTRAP_TAG."
                    else e.localizedMessage ?: "Неизвестная ошибка"

                    AlertDialog.Builder(this@TerminalActivity)
                        .setTitle("Ошибка установки")
                        .setMessage(msg)
                        .setPositiveButton("Повторить") { _, _ -> checkAndInstallBootstrap() }
                        .setNegativeButton("Выход") { _, _ -> finish() }
                        .show()
                }
            }
        }.start()
    }

    private fun downloadFile(urlStr: String, destFile: File) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true

        if (conn.responseCode >= 400) {
            throw IOException("HTTP ${conn.responseCode} для $urlStr")
        }

        conn.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output, 8192)
            }
        }
    }

    private fun unzipBootstrap(zipFile: File) {
        val rootDir = filesDir
        val buffer = ByteArray(8192)

        val stripPrefixes = listOf(
            "data/data/com.termux/files/",
            "data/data/${packageName}/files/"
        )

        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {

                var name = entry.name

                for (p in stripPrefixes) {
                    if (name.startsWith(p)) {
                        name = name.substring(p.length)
                        break
                    }
                }

                if (name.isEmpty()) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }

                val outputFile = File(rootDir, name)

                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }

                    if (
                        name.startsWith("usr/bin/") ||
                        name.startsWith("usr/libexec/") ||
                        name.endsWith(".so")
                    ) {
                        outputFile.setExecutable(true, false)
                        outputFile.setReadable(true, false)
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun applySymlinks() {
        val usrDir = File(filesDir, "usr")
        val symlinksFile = File(usrDir, "SYMLINKS.txt")
        if (!symlinksFile.exists()) return

        try {
            symlinksFile.useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split("←")
                    if (parts.size == 2) {
                        val linkFile = File(usrDir, parts[0].trim())
                        val target = parts[1].trim()

                        linkFile.parentFile?.mkdirs()
                        if (linkFile.exists()) linkFile.delete()
                        try {
                            Os.symlink(target, linkFile.absolutePath)
                        } catch (e: Exception) {
                            Log.w(TAG, "Symlink failed: ${parts[0]} -> $target", e)
                        }
                    }
                }
            }
            symlinksFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Symlinks error", e)
        }
    }

    private fun setupSession() {
        try {
            val usrDir = File(filesDir, "usr")
            val homeDir = File(filesDir, "home").apply { if (!exists()) mkdirs() }
            val tmpDir = File(usrDir, "tmp").apply { if (!exists()) mkdirs() }

            val bashFile = File(usrDir, "bin/bash")
            if (!bashFile.exists()) throw IOException("bash не найден: ${bashFile.absolutePath}")

            bashFile.setExecutable(true, false)

            val env = arrayOf(
                "TERM=xterm-256color",
                "HOME=${homeDir.absolutePath}",
                "PREFIX=${usrDir.absolutePath}",
                "PATH=${usrDir.absolutePath}/bin:${usrDir.absolutePath}/bin/applets",
                "LD_LIBRARY_PATH=${usrDir.absolutePath}/lib",
                "LANG=en_US.UTF-8",
                "TMPDIR=${tmpDir.absolutePath}"
            )

            terminalSession = TerminalSession(
                bashFile.absolutePath,
                homeDir.absolutePath,
                arrayOf("--login"),
                env,
                2000,
                this
            )

            terminalView.attachSession(terminalSession)
            terminalView.onScreenUpdated()

        } catch (e: Exception) {
            Log.e(TAG, "Session init failed", e)
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Ошибка терминала")
                    .setMessage(e.localizedMessage ?: "Ошибка инициализации")
                    .setPositiveButton("ОК") { _, _ -> finish() }
                    .show()
            }
        }
    }

    private fun showKeyboard() {
        terminalView.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm.isAcceptingText) {
            imm.hideSoftInputFromWindow(terminalView.windowToken, 0)
        } else {
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_FORCED)
        }
    }

    override fun onTextChanged(session: TerminalSession) { terminalView.onScreenUpdated() }
    override fun onTitleChanged(session: TerminalSession) {}
    override fun onSessionFinished(session: TerminalSession) { finish() }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("Termux", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.primaryClip?.getItemAt(0)?.text?.let { terminalSession?.write(it.toString()) }
    }

    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int = 0
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    override fun onSingleTapUp(e: MotionEvent) { showKeyboard() }
    override fun onLongPress(e: MotionEvent): Boolean = false
    override fun onScale(scale: Float): Float = scale
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onCodePoint(cp: Int, ctrl: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() {}
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false

    override fun logError(tag: String, msg: String) { Log.e(tag, msg) }
    override fun logWarn(tag: String, msg: String) { Log.w(tag, msg) }
    override fun logInfo(tag: String, msg: String) { Log.i(tag, msg) }
    override fun logDebug(tag: String, msg: String) { Log.d(tag, msg) }
    override fun logVerbose(tag: String, msg: String) { Log.v(tag, msg) }
    override fun logStackTraceWithMessage(tag: String, msg: String, e: Exception) { Log.e(tag, msg, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "Stack", e) }

    override fun onDestroy() {
        terminalSession?.finishIfRunning()
        super.onDestroy()
    }
}
