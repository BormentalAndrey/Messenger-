package com.kakdela.p2p.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

@Composable
fun PhoneAuthScreen(
    navController: NavHostController,
    onAuthSuccess: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val auth = Firebase.auth

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Вход по номеру телефона",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Номер телефона") },
            enabled = verificationId == null && !loading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        if (verificationId == null) {
            Button(
                onClick = {
                    if (phoneNumber.isBlank()) return@Button
                    loading = true
                    error = null

                    val callbacks =
                        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                            override fun onVerificationCompleted(
                                credential: PhoneAuthCredential
                            ) {
                                signIn(auth, credential, onAuthSuccess)
                            }

                            override fun onVerificationFailed(e: FirebaseException) {
                                loading = false
                                error = e.message
                            }

                            override fun onCodeSent(
                                id: String,
                                token: PhoneAuthProvider.ForceResendingToken
                            ) {
                                verificationId = id
                                loading = false
                            }
                        }

                    PhoneAuthProvider.getInstance(auth).verifyPhoneNumber(
                        phoneNumber,
                        60,
                        TimeUnit.SECONDS,
                        navController.context as ComponentActivity,
                        callbacks
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                } else {
                    Text("Отправить код")
                }
            }
        } else {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Код из SMS") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (code.isBlank()) return@Button
                    val credential =
                        PhoneAuthProvider.getCredential(verificationId!!, code)
                    signIn(auth, credential, onAuthSuccess)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Подтвердить")
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun signIn(
    auth: FirebaseAuth,
    credential: PhoneAuthCredential,
    onSuccess: () -> Unit
) {
    auth.signInWithCredential(credential)
        .addOnSuccessListener { onSuccess() }
}
