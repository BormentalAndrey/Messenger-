package com.kakdela.p2p.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kakdela.p2p.ui.theme.KakdelaTheme
import com.kakdela.p2p.viewmodel.FileManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(vm: FileManagerViewModel = viewModel()) {
    // Обработка кнопки "Назад"
    BackHandler(enabled = true) {
        if (!vm.goBack()) { /* Закрыть менеджер или выйти */ }
    }

    KakdelaTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Файлы", color = Color(0xFF00FFF0)) }, // NeonCyan
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                    navigationIcon = {
                        IconButton(onClick = { vm.goBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                        }
                    }
                )
            },
            containerColor = Color.Black
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                // Хлебные крошки (путь)
                Text(
                    text = vm.currentPath,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    maxLines = 1
                )

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(vm.filesList) { item ->
                        FileListItem(
                            item = item,
                            onClick = { vm.navigateTo(item.path) },
                            onDelete = { vm.deleteFile(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileListItem(item: com.kakdela.p2p.model.FileItem, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            tint = if (item.isDirectory) Color(0xFFFF00C8) else Color(0xFFD700FF), // NeonPink или NeonPurple
            modifier = Modifier.size(32.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(text = item.name, color = Color.White, fontSize = 16.sp)
            if (!item.isDirectory) {
                Text(text = "${item.size / 1024} KB", color = Color.Gray, fontSize = 12.sp)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE0E0E0))
        }
    }
}
