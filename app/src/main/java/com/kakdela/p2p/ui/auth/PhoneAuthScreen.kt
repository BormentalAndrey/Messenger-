package com.kakdela.p2p.ui.auth

import android.content.IntentFilter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kakdela.p2p.auth.SmsCodeManager
import com.kakdela.p2p.auth.SmsReceiver

@Composable
fun PhoneAuthScreen(
    navController: NavHostController,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current

    var phone by remember { mutableStateOf("") }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var inputCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val receiver = remember {
        SmsReceiver { code ->
            inputCode = code
        }
    }

    DisposableEffect(Unit) {
        context.registerReceiver(
            receiver,
            IntentFilter("android.provider.Telephony.SMS_RECEIVED")
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Номер телефона") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (phone.isBlank()) {
                    error = "Введите номер телефона"
                    return@Button
                }

                val code = SmsCodeManager.generateCode()
                generatedCode = code
                SmsCodeManager.sendCode(phone, code)
            }
        ) {
            Text("Отправить SMS с кодом")
        }

        generatedCode?.let {
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = inputCode,
                onValueChange = { inputCode = it },
                label = { Text("Код из SMS") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (inputCode == generatedCode) {
                        onSuccess()
                    } else {
                        error = "Неверный код"
                    }
                }
            ) {
                Text("Подтвердить")
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}
