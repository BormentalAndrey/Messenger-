package com.kakdela.p2p.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen() {
    var display by remember { mutableStateOf("0") }
    var firstNumber by remember { mutableStateOf("") }
    var operation by remember { mutableStateOf("") }
    var waitingForSecondNumber by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Калькулятор", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { label ->
                        Button(
                            onClick = {
                                when (label) {
                                    "C" -> {
                                        display = "0"
                                        firstNumber = ""
                                        operation = ""
                                        waitingForSecondNumber = false
                                    }
                                    "=" -> {
                                        // Расчёт (упрощённый)
                                        if (operation.isNotEmpty() && firstNumber.isNotEmpty()) {
                                            val second = display.toDouble()
                                            val result = when (operation) {
                                                "+" -> firstNumber.toDouble() + second
                                                "-" -> firstNumber.toDouble() - second
                                                "×" -> firstNumber.toDouble() * second
                                                "÷" -> firstNumber.toDouble() / second
                                                else -> second
                                            }
                                            display = result.toString().removeSuffix(".0")
                                            operation = ""
                                        }
                                    }
                                    "+", "-", "×", "÷" -> {
                                        firstNumber = display
                                        operation = label
                                        waitingForSecondNumber = true
                                    }
                                    else -> {
                                        if (waitingForSecondNumber || display == "0") {
                                            display = label
                                            waitingForSecondNumber = false
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
