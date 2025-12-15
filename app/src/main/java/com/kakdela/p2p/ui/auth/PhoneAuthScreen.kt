package com.kakdela.p2p.ui.auth

import android.Manifest
import android.content.IntentFilter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kakdela.p2p.auth.SmsCodeManager
import com.kakdela.p2p.auth.SmsReceiver
import kotlinx.coroutines.delay

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
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
            onClick = {
                val code = SmsCodeManager.generateCode()
                generatedCode = code
                SmsCodeManager.sendCode(phone, code)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Отправить код")
        }

        if (generatedCode != null) {
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
                onClick = {
                    if (inputCode == generatedCode) {
                        onSuccess()
                    } else {
                        error = "Неверный код"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Подтвердить")
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
