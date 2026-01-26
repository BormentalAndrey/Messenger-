package com.kakdela.p2p.ui.terminal

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import java.io.File

class TerminalActivity : AppCompatActivity() {

    private lateinit var terminalView: TerminalView
    private var session: TerminalSession? = null
    private val TAG = "TerminalActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        terminalView = TerminalView(this, null).apply {
            setTextSize(16)
            keepScreenOn = true
        }
        
        setContentView(terminalView as View)

        setupTerminalSession()
    }

    private fun setupTerminalSession() {
        try {
            val termuxPrefix = File(filesDir, "usr")
            val homeDir = File(filesDir, "home")

            if (!termuxPrefix.exists()) termuxPrefix.mkdirs()
            if (!homeDir.exists()) homeDir.mkdirs()

            val env = arrayOf(
                "PATH=${termuxPrefix.absolutePath}/bin:/system/bin:/system/xbin",
                "HOME=${homeDir.absolutePath}",
                "TERM=xterm-256color",
                "PREFIX=${termuxPrefix.absolutePath}"
            )

            val shellPath = if (File(termuxPrefix, "bin/bash").exists()) {
                "${termuxPrefix.absolutePath}/bin/bash"
            } else {
                "/system/bin/sh"
            }

            // Создаем клиент с правильными именами методов согласно логам ошибок
            val client = object : TerminalSessionClient {
                override fun onTextChanged(session: TerminalSession) {
                    terminalView.onScreenUpdated()
                }

                override fun onTitleChanged(session: TerminalSession) {
                    Log.d(TAG, "Title: ${session.title}")
                }

                override fun onSessionFinished(session: TerminalSession) {
                    if (!isFinishing) finish()
                }

                // Исправлено согласно логу: onCopyTextToClipboard вместо onClipboardText
                override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
                    // Логика копирования в буфер обмена (по желанию)
                }

                override fun onBell(session: TerminalSession) {}
                override fun onColorsChanged(session: TerminalSession) {}
                override fun onTerminalCursorStateChange(state: Boolean) {}
                
                // В новых версиях этот метод может принимать Integer
                override fun setTerminalShellProcessId(session: TerminalSession, processId: Int) {}
                
                // Дополнительные методы, если они требуются интерфейсом
                override fun onPasteTextFromClipboard(session: TerminalSession) {}
                override fun onIntegerConfigNotify(session: TerminalSession, p1: Int, p2: Int) {}
            }

            // Важно: Проверьте порядок аргументов. В некоторых версиях Termux:
            // TerminalSession(shellPath, cwd, args, env, client)
            session = TerminalSession(
                shellPath,
                homeDir.absolutePath,
                arrayOf("-l"),
                env,
                client
            )

            terminalView.attachSession(session)

        } catch (e: Exception) {
            Log.e(TAG, "Terminal setup failed", e)
        }
    }

    override fun onDestroy() {
        // Используем стандартный метод завершения
        session?.finishIfRunning()
        super.onDestroy()
    }
}
