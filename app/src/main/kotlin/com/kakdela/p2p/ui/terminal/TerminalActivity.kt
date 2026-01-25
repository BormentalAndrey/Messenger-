package com.kakdela.p2p.ui.terminal

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView // ИСПРАВЛЕНО: Правильный пакет для View
import java.io.File

class TerminalActivity : AppCompatActivity() {

    private lateinit var terminalView: TerminalView
    private var session: TerminalSession? = null
    private val TAG = "TerminalActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // -------------------- TerminalView --------------------
        // Используем конструктор с Context и AttributeSet (null)
        terminalView = TerminalView(this, null).apply {
            setTextSize(16) // Увеличили для лучшей читаемости в продакшне
            keepScreenOn = true
        }
        
        // ИСПРАВЛЕНО: Устраняем "Overload resolution ambiguity" явным приведением к View
        setContentView(terminalView as View)

        // -------------------- Termux Shell --------------------
        setupTerminalSession()
    }

    private fun setupTerminalSession() {
        try {
            // Определяем рабочие директории
            val termuxPrefix = File(filesDir, "usr")
            val homeDir = File(filesDir, "home")

            if (!termuxPrefix.exists()) termuxPrefix.mkdirs()
            if (!homeDir.exists()) homeDir.mkdirs()

            // Настройка окружения (Environment)
            // В продакшне важно прописать PATH, чтобы работали базовые команды
            val env = mutableListOf(
                "PATH=${termuxPrefix.absolutePath}/bin:/system/bin:/system/xbin",
                "HOME=${homeDir.absolutePath}",
                "TERM=xterm-256color",
                "PREFIX=${termuxPrefix.absolutePath}"
            )

            // Определяем исполняемый файл
            val shellPath = if (File(termuxPrefix, "bin/bash").exists()) {
                "${termuxPrefix.absolutePath}/bin/bash"
            } else if (File(termuxPrefix, "bin/sh").exists()) {
                "${termuxPrefix.absolutePath}/bin/sh"
            } else {
                "/system/bin/sh"
            }

            // ИСПРАВЛЕНО: Прямое создание TerminalSession (надежнее для встраивания)
            // Параметры: shellPath, cwd, args, env, clientCallback
            session = TerminalSession(
                shellPath,
                homeDir.absolutePath,
                arrayOf("-l"), // login shell
                env.toTypedArray(),
                object : com.termux.terminal.TerminalSessionClient {
                    override fun onTextChanged(session: TerminalSession) {
                        terminalView.onScreenUpdated()
                    }
                    override fun onCommandFinished(session: TerminalSession) {
                        Log.i(TAG, "Shell session finished")
                        if (!isFinishing) finish()
                    }
                    override fun onSessionTitleChanged(session: TerminalSession) {}
                    override fun onClipboardText(session: TerminalSession, text: String) {}
                    override fun onBell(session: TerminalSession) {}
                    override fun onColorsChanged(session: TerminalSession) {}
                    override fun onTerminalCursorStateChange(state: Boolean) {}
                    override fun setTerminalShellProcessId(processId: Int) {}
                }
            )

            // Присоединяем сессию к View
            terminalView.attachSession(session)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Terminal session", e)
        }
    }

    override fun onResume() {
        super.onResume()
        // Обеспечиваем фокус для клавиатуры
        terminalView.requestFocus()
    }

    override fun onDestroy() {
        // ИСПРАВЛЕНО: Явный вызов finish() для процесса shell
        session?.finishIfRunning()
        super.onDestroy()
    }
}
