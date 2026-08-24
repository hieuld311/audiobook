package com.ivi.audiobook.presentation.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivi.audiobook.presentation.theme.OnSurfaceSecondary
import com.ivi.audiobook.util.StoragePermissions

@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var permissionGranted by remember { mutableStateOf(StoragePermissions.isGranted()) }

    // All Files Access is granted via a Settings screen, not a permission dialog, so there's no
    // reliable result code to read — just re-check the real state when the user comes back.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        permissionGranted = StoragePermissions.isGranted()
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.refresh()
    }

    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(uiState.books.size) {
        if (focusedIndex > uiState.books.lastIndex) focusedIndex = uiState.books.lastIndex.coerceAtLeast(0)
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            !permissionGranted -> PermissionPrompt(
                onRequest = { permissionLauncher.launch(StoragePermissions.requestIntent(context)) },
                modifier = Modifier.fillMaxSize(),
            )
            uiState.books.isEmpty() -> EmptyLibraryMessage(isScanning = uiState.isScanning, modifier = Modifier.fillMaxSize())
            else -> CompactLibraryBar(
                books = uiState.books,
                focusedIndex = focusedIndex,
                onFocusChanged = { focusedIndex = it },
                onOpenBook = onOpenBook,
                onRefresh = viewModel::resetLibrary,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Allow All Files Access to find audiobooks",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Internal storage and any plugged-in USB drive will be scanned automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            Button(onClick = onRequest) { Text("Grant access") }
        }
    }
}

@Composable
private fun EmptyLibraryMessage(isScanning: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = if (isScanning) "Scanning for audiobooks…" else "No audiobooks found yet",
            style = MaterialTheme.typography.titleMedium,
            color = OnSurfaceSecondary,
        )
    }
}
