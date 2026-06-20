package com.lossless.music.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

/**
 * 内置文件浏览器。
 *
 * 浏览 App 可访问目录(默认 filesDir/music),显示音频文件和文件夹。
 * 点击文件直接播放,勾选后可批量导入到音乐库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val currentDir by viewModel.currentDir.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val importedCount by viewModel.importedCount.collectAsStateWithLifecycle()
    var showImportSnackbar by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(importedCount) {
        if (importedCount > 0) {
            snackbarHost.showSnackbar("已导入 $importedCount 首歌曲")
            showImportSnackbar = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文件浏览器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = { viewModel.importSelected() }) {
                            Text("导入 ${selected.size} 首")
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 路径栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (!viewModel.navigateUp()) onBack() }) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "上一级")
                }
                Text(
                    currentDir.path,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Divider()

            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("此目录为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.file.path }) { entry ->
                        FileEntryItem(
                            entry = entry,
                            isSelected = selected.contains(entry.file),
                            onClick = {
                                if (entry.isDirectory) {
                                    viewModel.navigateInto(entry.file)
                                } else {
                                    viewModel.playFile(entry.file)
                                    onNavigateToPlayer()
                                }
                            },
                            onToggleSelect = {
                                if (!entry.isDirectory) {
                                    viewModel.toggleSelection(entry.file)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileEntryItem(
    entry: FileEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit
) {
    ListItem(
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (!entry.isDirectory) {
                Text(formatFileSize(entry.file.length()), style = MaterialTheme.typography.bodySmall)
            }
        },
        leadingContent = {
            Icon(
                if (entry.isDirectory) Icons.Default.Folder else Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (entry.isDirectory) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            if (!entry.isDirectory) {
                IconButton(onClick = onToggleSelect) {
                    Icon(
                        if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "已选择" else "选择",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
