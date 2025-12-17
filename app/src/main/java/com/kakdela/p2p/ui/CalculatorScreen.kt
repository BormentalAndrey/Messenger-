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
    var first by remember { mutableStateOf("") }
    var op by remember { mutableStateOf("") }
    var waitSecond by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Калькулятор", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padd ->
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(display, color = Color.White, fontSize = 48.sp, modifier = Modifier.padding(16.dp))

            val rows = listOf(
                listOf("C", "+/-", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "=")
            )

            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { b ->
                        Button(
                            onClick = {
                                when (b) {
                                    "C" -> {
                                        display = "0"
                                        first = ""
                                        op = ""
                                        waitSecond = false
                                    }
                                    "=" -> {
                                        if (op.isNotEmpty() && first.isNotEmpty()) {
                                            val s = display.toDouble()
                                            val r = when (op) {
                                                "+" -> first.toDouble() + s
                                                "-" -> first.toDouble() - s
                                                "×" -> first.toDouble() * s
                                                "÷" -> first.toDouble() / s
                                                else -> s
                                            }
                                            display = r.toString().removeSuffix(".0")
                                            op = ""
                                        }
                                    }
                                    "+", "-", "×", "÷" -> {
                                        first = display
                                        op = b
                                        waitSecond = true
                                    }
                                    else -> {
                                        if (waitSecond || display == "0") {
                                            display = b
                                            waitSecond = false
                                        } else {
                                            display += b
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
                        ) {
                            Text(b, fontSize = 24.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
