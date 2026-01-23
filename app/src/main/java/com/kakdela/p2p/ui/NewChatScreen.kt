package com.kakdela.p2p.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kakdela.p2p.data.IdentityRepository
import kotlinx.coroutines.launch

@Composable
fun NewChatScreen(
    navController: NavHostController,
    identityRepository: IdentityRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val myHash = identityRepository.getMyId()
    val peers = remember { mutableStateListOf<Pair<String, String>>() }
    val selected = remember { mutableStateListOf<String>() }

    Column(Modifier.fillMaxSize().padding(24.dp)) {

        Text("Новый чат", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Номер телефона") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (phone.isBlank()) {
                    error = "Введите номер"
                    return@Button
                }

                loading = true
                scope.launch {
                    val ok = identityRepository.addNodeByHash(
                        identityRepository.generatePhoneDiscoveryHash(phone)
                    )
                    loading = false

                    if (ok) {
                        navController.navigate("chats")
                    } else {
                        error = "Пользователь не найден"
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp))
            else Text("Начать чат")
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        Text("Групповой чат")

        LazyColumn(Modifier.weight(1f)) {
            items(peers) { (hash, phone) ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            if (selected.contains(hash)) selected.remove(hash)
                            else selected.add(hash)
                        }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(phone)
                    if (selected.contains(hash)) Text("✓")
                }
            }
        }

        if (selected.size >= 2) {
            Button(
                onClick = {
                    Toast.makeText(
                        context,
                        "Группа создана (${selected.size + 1})",
                        Toast.LENGTH_SHORT
                    ).show()
                    navController.navigate("chats")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Создать группу")
            }
        }
    }

    LaunchedEffect(Unit) {
        val nodes = identityRepository.fetchAllNodesFromServer()
        peers.clear()
        peers.addAll(
            nodes.filter { it.hash != myHash }
                .map { it.hash to (it.phone ?: it.hash.take(8)) }
        )
    }
}
