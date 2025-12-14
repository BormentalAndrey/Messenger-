package com.kakdela.p2p.utils

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun rememberContactsPermissionLauncher(onGranted: () -> Unit): () -> Unit {
    val permissionState = remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permissionState.value = true
            onGranted()
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(android.Manifest.permission.READ_CONTACTS)
    }

    return { launcher.launch(android.Manifest.permission.READ_CONTACTS) }
}
