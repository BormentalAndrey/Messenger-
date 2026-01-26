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

            // Создаем клиент с учетом nullability параметров (p0: TerminalSession?)
            val client = object : TerminalSessionClient {
                override fun onTextChanged(session: TerminalSession?) {
                    terminalView.onScreenUpdated()
                }

                override fun onTitleChanged(session: TerminalSession?) {
                    Log.d(TAG, "Title changed")
                }

                override fun onSessionFinished(session: TerminalSession?) {
                    if (!isFinishing) finish()
                }

                override fun onCopyTextToClipboard(session: TerminalSession?, text: String?) {}

                // Исправлено: добавлены обязательные знаки вопроса (?) для соответствия интерфейсу
                override fun onPasteTextFromClipboard(session: TerminalSession?) {}

                override fun onBell(session: TerminalSession?) {}

                override fun onColorsChanged(session: TerminalSession?) {}

                override fun onTerminalCursorStateChange(state: Boolean) {}

                // В некоторых версиях этот метод может отсутствовать или иметь другую сигнатуру
                // Если ошибка 'overrides nothing' сохранится, удалите @Override или сам метод
                fun setTerminalShellProcessId(processId: Int) {}
            }

            /* ИСПРАВЛЕНИЕ КОНСТРУКТОРА: 
               Судя по ошибке "No value passed for parameter 'p5'", ваша версия ожидает 
               минимум 6 параметров. Обычно это:
               1. shellPath, 2. cwd, 3. args, 4. env, 5. client, 6. ПАРАМЕТР (часто transcriptRows)
            */
            session = TerminalSession(
                shellPath,
                homeDir.absolutePath,
                arrayOf("-l"),
                env,
                client,
                10000 // p5: количество строк истории (transcript rows)
            )

            terminalView.attachSession(session)

        } catch (e: Exception) {
            Log.e(TAG, "Terminal setup failed", e)
        }
    }

    override fun onDestroy() {
        // Безопасное завершение сессии
        try {
            session?.finishIfRunning()
        } catch (e: Exception) {
            Log.e(TAG, "Error finishing session", e)
        }
        super.onDestroy()
    }
}

