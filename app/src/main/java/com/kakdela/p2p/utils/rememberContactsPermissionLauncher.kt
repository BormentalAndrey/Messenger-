package com.kakdela.p2p.utils

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun rememberContactsPermissionLauncher(
    onGranted: () -> Unit
): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onGranted()
    }

    return {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            onGranted()
        } else {
            launcher.launch(Manifest.permission.READ_CONTACTS)
        }
    }
}
