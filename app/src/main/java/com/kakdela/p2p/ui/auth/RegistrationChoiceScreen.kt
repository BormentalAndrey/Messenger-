@Composable
fun RegistrationChoiceScreen(
    onPhone: () -> Unit,
    onEmailOnly: () -> Unit // Опционально, если разрешаем вход только по почте
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Создайте свой P2P профиль", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(48.dp))

        // Основной путь: сначала телефон (это создает ключи и ID)
        Button(onClick = onPhone, modifier = Modifier.fillMaxWidth()) {
            Text("Создать личность по номеру")
        }

        Spacer(Modifier.height(16.dp))
        
        Text("или", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = onEmailOnly, modifier = Modifier.fillMaxWidth()) {
            Text("Войти через Email (Legacy)")
        }
    }
}

