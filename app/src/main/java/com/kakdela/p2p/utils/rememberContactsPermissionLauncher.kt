package com.kakdela.p2p.utils

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity

/**
 * Composable функция для запроса разрешения на чтение контактов.
 * Возвращает лямбду, которую можно вызвать для запуска запроса.
 * Вызов onGranted происходит после предоставления разрешения.
 */
@Composable
fun rememberContactsPermissionLauncher(
    onGranted: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    val launcher: ActivityResultLauncher<String> = remember {
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                onGranted()
            }
            // Можно добавить обработку отказа
        }
    }

    return {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) -> onGranted()
            else -> launcher.launch(Manifest.permission.READ_CONTACTS)
        }
    }
}
