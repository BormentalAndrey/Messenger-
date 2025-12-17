
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(chatId: String, currentUserId: String) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel()
    val messages by viewModel.messages.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    var text by remember { mutableStateOf("") }
    var p2pImage by remember { mutableStateOf<ByteArray?>(null) } // Для временного хранения принятого P2P фото

    // Инициализация WebRTC клиента
    val rtcClient = remember {
        WebRtcClient(context, chatId, currentUserId) { receivedBytes ->
            p2pImage = receivedBytes // Файл пришел напрямую!
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(it)?.readBytes()
                bytes?.let { b ->
                    // 1. Уведомляем систему, что сейчас пойдет P2P передача
                    viewModel.send(chatId, Message(text = "Отправляю файл напрямую...", senderId = currentUserId, isP2P = true))
                    // 2. Пытаемся соединиться и отправить
                    rtcClient.startConnection()
                    rtcClient.sendFile(b)
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp).background(Color(0xFF1A1A1A), RoundedCornerShape(24.dp)), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { launcher.launch("image/*") }) {
                    Icon(Icons.Default.AttachFile, null, tint = Color.Cyan)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f).padding(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White)
                )
                IconButton(onClick = { 
                    viewModel.send(chatId, Message(text = text, senderId = currentUserId))
                    text = "" 
                }) {
                    Icon(painterResource(android.R.drawable.ic_menu_send), null, tint = Color.Cyan)
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(messages) { msg ->
                ChatBubble(msg, msg.senderId == currentUserId, p2pImage)
            }
        }
    }
}

@Composable
fun ChatBubble(msg: Message, isOwn: Boolean, p2pImage: ByteArray?) {
    val alignment = if (isOwn) Alignment.End else Alignment.Start
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = alignment) {
        Surface(shape = RoundedCornerShape(12.dp), color = if (isOwn) Color(0xFF005C4B) else Color(0xFF202C33)) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (msg.isP2P && !isOwn && p2pImage != null) {
                    // Отображаем принятое напрямую фото
                    val bitmap = BitmapFactory.decodeByteArray(p2pImage, 0, p2pImage.size)
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(200.dp))
                }
                Text(msg.text, color = Color.White)
            }
        }
    }
}
