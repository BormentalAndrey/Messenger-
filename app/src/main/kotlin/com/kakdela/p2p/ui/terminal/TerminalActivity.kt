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

            val client = object : TerminalSessionClient {
                override fun onTextChanged(session: TerminalSession) {
                    terminalView.onScreenUpdated()
                }

                override fun onTitleChanged(session: TerminalSession) {
                    Log.d(TAG, "Title changed")
                }

                override fun onSessionFinished(session: TerminalSession) {
                    if (!isFinishing) finish()
                }

                override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}

                override fun onPasteTextFromClipboard(session: TerminalSession?) {}

                override fun onBell(session: TerminalSession) {}

                override fun onColorsChanged(session: TerminalSession) {}

                override fun onTerminalCursorStateChange(state: Boolean) {}

                override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
                    Log.d(TAG, "Shell PID: $pid")
                }

                override fun getTerminalCursorStyle(): Int {
                    return 0 
                }

                // Логирование (Error, Warn, Info, Debug уже были, добавляем Verbose)
                override fun logError(tag: String?, message: String?) { Log.e(tag, message ?: "") }
                override fun logWarn(tag: String?, message: String?) { Log.w(tag, message ?: "") }
                override fun logInfo(tag: String?, message: String?) { Log.i(tag, message ?: "") }
                override fun logDebug(tag: String?, message: String?) { Log.d(tag, message ?: "") }
                
                // ИСПРАВЛЕНО: Добавлен Verbose согласно логу ошибки
                override fun logVerbose(tag: String?, message: String?) {
                    Log.v(tag, message ?: "")
                }
                
                // Превентивное добавление (на случай если библиотека потребует StackTrace)
                fun logStackTrace(tag: String?, e: Exception?) {
                    Log.e(tag, Log.getStackTraceString(e))
                }
            }

            // Порядок: [Path, CWD, Args, Env, TranscriptRows, Client]
            session = TerminalSession(
                shellPath,
                homeDir.absolutePath,
                arrayOf("-l"),
                env,
                10000, 
                client
            )

            terminalView.attachSession(session)

        } catch (e: Exception) {
            Log.e(TAG, "Terminal setup failed", e)
        }
    }

    override fun onDestroy() {
        try {
            session?.finishIfRunning()
        } catch (e: Exception) {
            Log.e(TAG, "Error finishing session", e)
        }
        super.onDestroy()
    }
}
