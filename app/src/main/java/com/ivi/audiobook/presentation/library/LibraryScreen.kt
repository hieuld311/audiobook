package com.ivi.audiobook.presentation.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivi.audiobook.presentation.components.BookCoverCard
import com.ivi.audiobook.presentation.components.LibraryHeaderBar
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

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!permissionGranted) {
            PermissionPrompt(
                onRequest = { permissionLauncher.launch(StoragePermissions.requestIntent(context)) },
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        LibraryHeaderBar(
            bookCount = uiState.books.size,
            query = uiState.query,
            onSearchTextChange = viewModel::onSearchTextChange,
            onSortOrderChange = viewModel::onSortOrderChange,
            onSourceChange = viewModel::onSourceChange,
            onHideFinishedChange = viewModel::onHideFinishedChange,
            onRefresh = viewModel::resetLibrary,
        )

        if (uiState.books.isEmpty()) {
            EmptyLibraryMessage(isScanning = uiState.isScanning, modifier = Modifier.fillMaxSize())
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(uiState.books, key = { it.id }) { book ->
                    BookCoverCard(book = book, onClick = { onOpenBook(book.id) })
                }
            }
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
