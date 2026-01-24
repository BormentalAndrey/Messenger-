package com.kakdela.p2p.ui.terminal

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.concurrent.thread

class TerminalActivity : AppCompatActivity() {

    private lateinit var outputView: TextView
    private lateinit var inputView: EditText

    private lateinit var shellProcess: Process
    private lateinit var shellInput: OutputStreamWriter
    private lateinit var shellOutput: BufferedReader

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
            singleLine = true
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
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                val cmd = inputView.text.toString()
                inputView.setText("")
                sendCommand(cmd)
                true
            } else false
        }
    }

    /* ---------------- Shell logic ---------------- */

    private fun startShell() {
        shellProcess = ProcessBuilder("/system/bin/sh")
            .redirectErrorStream(true)
            .start()

        shellInput = OutputStreamWriter(shellProcess.outputStream)
        shellOutput = BufferedReader(InputStreamReader(shellProcess.inputStream))

        thread {
            var line: String?
            while (shellOutput.readLine().also { line = it } != null) {
                runOnUiThread {
                    outputView.append("$line\n")
                    scrollToBottom()
                }
            }
        }
    }

    private fun sendCommand(command: String) {
        outputView.append("\n$ $command\n")
        scrollToBottom()

        thread {
            shellInput.write(command + "\n")
            shellInput.flush()
        }
    }

    private fun scrollToBottom() {
        val layout = outputView.layout ?: return
        val scroll = layout.getLineTop(outputView.lineCount) - outputView.height
        if (scroll > 0) {
            outputView.scrollTo(0, scroll)
        } else {
            outputView.scrollTo(0, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shellProcess.destroy()
    }
}
