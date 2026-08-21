package com.ivi.audiobook.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ivi.audiobook.domain.model.LibraryQuery
import com.ivi.audiobook.domain.model.SortOrder
import com.ivi.audiobook.domain.model.SourceFilter
import com.ivi.audiobook.presentation.theme.OnSurfaceSecondary
import com.ivi.audiobook.presentation.theme.SurfaceVariantDark

private fun SortOrder.label() = when (this) {
    SortOrder.TITLE -> "Title"
    SortOrder.AUTHOR -> "Author (First Last)"
    SortOrder.RECENTLY_ADDED -> "Recently Added"
    SortOrder.RECENTLY_PLAYED -> "Recently Played"
}

private fun SourceFilter.label() = when (this) {
    SourceFilter.ALL -> "All"
    SourceFilter.INTERNAL -> "Internal"
    SourceFilter.USB -> "USB"
}

@Composable
fun LibraryHeaderBar(
    bookCount: Int,
    query: LibraryQuery,
    onSearchTextChange: (String) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onSourceChange: (SourceFilter) -> Unit,
    onHideFinishedChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceVariantDark)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$bookCount Books",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )

        SearchField(
            value = query.searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.width(360.dp),
        )

        HeaderSpacer()

        Checkbox(checked = query.hideFinished, onCheckedChange = onHideFinishedChange)
        Text(
            text = "Hide finished",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceSecondary,
        )

        HeaderSpacer()

        LabeledDropdown(
            label = query.source.label(),
            options = SourceFilter.entries,
            optionLabel = { it.label() },
            onSelected = onSourceChange,
        )

        HeaderSpacer()

        LabeledDropdown(
            label = query.sortOrder.label(),
            options = SortOrder.entries,
            optionLabel = { it.label() },
            onSelected = onSortOrderChange,
        )

        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh library", tint = OnSurfaceSecondary)
        }
    }
}

@Composable
private fun RowScope.HeaderSpacer() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .border(width = 1.dp, color = Color.White, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = OnSurfaceSecondary, modifier = Modifier.padding(end = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun <T> LabeledDropdown(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(end = 4.dp),
        )
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Change $label", tint = OnSurfaceSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = {
                    onSelected(option)
                    expanded = false
                })
            }
        }
    }
}
