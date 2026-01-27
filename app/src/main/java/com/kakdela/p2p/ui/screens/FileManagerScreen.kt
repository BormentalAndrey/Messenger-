package com.kakdela.p2p.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kakdela.p2p.model.FileItem
import com.kakdela.p2p.ui.theme.KakdelaTheme
import com.kakdela.p2p.viewmodel.FileManagerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    onExit: () -> Unit,
    vm: FileManagerViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var searchQuery by remember { mutableStateOf("") }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    // Обработка кнопки "Назад" на устройстве
    BackHandler(enabled = true) {
        if (!vm.goBack()) {
            onExit() // Если мы в корне, выходим с экрана
        }
    }

    KakdelaTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Файлы", color = Color(0xFF00FFF0)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            if (!vm.goBack()) {
                                onExit()
                            }
                        }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showNewFolderDialog = true }) {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                )
            },
            containerColor = Color.Black
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF00FFF0),
                        focusedBorderColor = Color(0xFF00FFF0),
                        unfocusedBorderColor = Color.Gray,
                        focusedPlaceholderColor = Color.Gray,
                        unfocusedPlaceholderColor = Color.Gray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )

                // Хлебные крошки (путь)
                Row(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val parts = vm.currentPath.split("/").filter { it.isNotEmpty() }
                    parts.forEachIndexed { idx, folder ->
                        Text(
                            text = folder,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                vm.navigateTo(vm.pathUpToIndex(idx + 1))
                            }
                        )
                        if (idx != parts.lastIndex) {
                            Text(" / ", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val filteredList =
                        if (searchQuery.isEmpty()) vm.filesList
                        else vm.filesList.filter {
                            it.name.contains(searchQuery, ignoreCase = true)
                        }

                    items(filteredList) { item ->
                        FileListItem(
                            item = item,
                            isSelected = vm.selectedFiles.contains(item),
                            onClick = {
                                if (vm.isSelectionMode) {
                                    vm.toggleSelection(item)
                                } else if (item.isDirectory) {
                                    vm.navigateTo(item.path)
                                } else {
                                    // Открытие файла через Intent
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(item.path), "*/*")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Обработка ошибки открытия
                                    }
                                }
                            },
                            onLongClick = { vm.toggleSelection(item) },
                            onDelete = {
                                scope.launch {
                                    vm.deleteFileWithConfirmation(item)
                                }
                            },
                            onRename = { newName ->
                                vm.renameFile(item, newName)
                            },
                            onCopy = { vm.copyFile(item) },
                            onProperties = {
                                vm.showFileProperties(item, context)
                            },
                            onDropOn = { targetFolder ->
                                vm.moveFilesTo(targetFolder)
                            }
                        )
                    }
                }

                if (showNewFolderDialog) {
                    NewFolderDialog(
                        onDismiss = { showNewFolderDialog = false },
                        onCreate = { folderName ->
                            vm.createFolder(folderName)
                            showNewFolderDialog = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FileListItem(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onCopy: () -> Unit,
    onProperties: () -> Unit,
    onDropOn: (FileItem) -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }

    val bgColor =
        if (isSelected) Color(0xFF00FFF0).copy(alpha = 0.3f)
        else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .pointerInput(item) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onLongClick() },
                    onDragEnd = {},
                    onDragCancel = {},
                    onDrag = { change, _ -> change.consume() }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = if (item.isDirectory)
                    Icons.Default.Folder
                else
                    Icons.Default.Description,
                contentDescription = null,
                tint = if (item.isDirectory)
                    Color(0xFFFF00C8)
                else
                    Color(0xFFD700FF),
                modifier = Modifier.size(32.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 16.sp
                )

                if (!item.isDirectory) {
                    Text(
                        text = "${item.size / 1024} KB",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            IconButton(onClick = { showOptions = !showOptions }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = Color(0xFFE0E0E0)
                )
            }
        }

        if (showOptions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.DarkGray)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                }
                IconButton(onClick = { onRename(item.name + "_renamed") }) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Yellow)
                }
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Cyan)
                }
                IconButton(onClick = onProperties) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color.Green)
                }
            }
        }
    }
}

@Composable
fun NewFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая папка") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                placeholder = { Text("Имя папки") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (folderName.isNotBlank()) {
                        onCreate(folderName)
                    }
                }
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
