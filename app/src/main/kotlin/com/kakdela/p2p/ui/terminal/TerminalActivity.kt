package com.kakdela.p2p.ui.terminal

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.concurrent.thread

class TerminalActivity : AppCompatActivity() {

    private lateinit var outputView: TextView
    private lateinit var inputView: EditText

    private var shellProcess: Process? = null
    private var shellInput: OutputStreamWriter? = null
    private var shellOutput: BufferedReader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* ---------------- UI ---------------- */

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }

        outputView = TextView(this).apply {
            setTextColor(0xFF00FF00.toInt())
            textSize = 14f
            setPadding(16, 16, 16, 16)
            movementMethod = ScrollingMovementMethod()
            text = "Kakdela Terminal\nType commands below\n\n"
        }

        inputView = EditText(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF111111.toInt())
            setPadding(16, 16, 16, 16)
            imeOptions = EditorInfo.IME_ACTION_DONE
            // Исправлено: используем современное свойство или метод
            isSingleLine = true 
            hint = "command"
            setHintTextColor(0xFF888888.toInt())
        }

        root.addView(
            outputView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(
            inputView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)

        /* ---------------- Shell ---------------- */

        startShell()

        inputView.setOnEditorActionListener { _, actionId, event ->
            if (
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val cmd = inputView.text.toString()
                if (cmd.isNotBlank()) {
                    inputView.setText("")
                    sendCommand(cmd)
                }
                true
            } else false
        }
    }

    /* ---------------- Shell logic ---------------- */

    private fun startShell() {
        try {
            val process = ProcessBuilder("/system/bin/sh")
                .redirectErrorStream(true)
                .start()
            
            shellProcess = process
            shellInput = OutputStreamWriter(process.outputStream)
            shellOutput = BufferedReader(InputStreamReader(process.inputStream))

            thread(start = true, isDaemon = true) {
                try {
                    var line: String?
                    while (shellOutput?.readLine().also { line = it } != null) {
                        runOnUiThread {
                            outputView.append("$line\n")
                            scrollToBottom()
                        }
                    }
                } catch (e: IOException) {
                    runOnUiThread { outputView.append("\n[Shell disconnected]\n") }
                }
            }
        } catch (e: IOException) {
            outputView.append("Failed to start shell: ${e.message}")
        }
    }

    private fun sendCommand(command: String) {
        outputView.append("\n$ $command\n")
        scrollToBottom()

        thread {
            try {
                shellInput?.write(command + "\n")
                shellInput?.flush()
            } catch (e: IOException) {
                runOnUiThread { outputView.append("Error sending command: ${e.message}\n") }
            }
        }
    }

    private fun scrollToBottom() {
        outputView.post {
            val layout = outputView.layout ?: return@post
            val scroll = layout.getLineTop(outputView.lineCount) - outputView.height
            if (scroll > 0) {
                outputView.scrollTo(0, scroll)
            } else {
                outputView.scrollTo(0, 0)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            shellInput?.close()
            shellOutput?.close()
            shellProcess?.destroy()
        } catch (e: Exception) {
            // Игнорируем ошибки при закрытии
        }
    }
}
