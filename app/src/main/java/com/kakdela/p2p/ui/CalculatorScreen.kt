package com.kakdela.p2p.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen() {

    var display by remember { mutableStateOf("0") }
    var firstNumber by remember { mutableStateOf<Double?>(null) }
    var operation by remember { mutableStateOf<String?>(null) }
    var waitingForSecond by remember { mutableStateOf(false) }

    fun clear() {
        display = "0"
        firstNumber = null
        operation = null
        waitingForSecond = false
    }

    fun calculate(second: Double): String {
        val first = firstNumber ?: return second.toString()
        val result = when (operation) {
            "+" -> first + second
            "-" -> first - second
            "×" -> first * second
            "÷" -> if (second == 0.0) return "Ошибка" else first / second
            else -> second
        }
        return result.toString().removeSuffix(".0")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Калькулятор",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {

            Text(
                text = display,
                color = Color.White,
                fontSize = 48.sp,
                modifier = Modifier.padding(16.dp)
            )

            val buttons = listOf(
                listOf("C", "+/-", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "=")
            )

            buttons.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { label ->
                        Button(
                            onClick = {
                                when (label) {

                                    "C" -> clear()

                                    "+/-" -> {
                                        if (display != "0") {
                                            display = if (display.startsWith("-"))
                                                display.drop(1)
                                            else
                                                "-$display"
                                        }
                                    }

                                    "%" -> {
                                        display =
                                            (display.toDoubleOrNull()?.div(100))?.toString()
                                                ?.removeSuffix(".0") ?: display
                                    }

                                    "+", "-", "×", "÷" -> {
                                        firstNumber = display.toDoubleOrNull()
                                        operation = label
                                        waitingForSecond = true
                                    }

                                    "=" -> {
                                        val second = display.toDoubleOrNull() ?: return@Button
                                        display = calculate(second)
                                        firstNumber = display.toDoubleOrNull()
                                        operation = null
                                        waitingForSecond = false
                                    }

                                    else -> {
                                        if (waitingForSecond || display == "0") {
                                            display = label
                                            waitingForSecond = false
                                        } else {
                                            display += label
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A2A2A)
                            )
                        ) {
                            Text(label, fontSize = 24.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
