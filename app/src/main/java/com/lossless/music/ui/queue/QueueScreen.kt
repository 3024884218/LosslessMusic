package com.lossless.music.ui.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lossless.music.domain.model.Song
import com.lossless.music.ui.home.formatDuration

/**
 * 播放队列页。
 * 分区显示:
 *  1. 当前播放
 *  2. 下一首队列(Play Next,优先级最高)
 *  3. 主队列(后续播放)
 *
 * 支持:播放、移除、清空、上移/下移调整顺序。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    viewModel: QueueViewModel = hiltViewModel()
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val nextQueue by viewModel.nextQueue.collectAsStateWithLifecycle()
    val mainQueue by viewModel.mainQueue.collectAsStateWithLifecycle()
    var showClearMainDialog by remember { mutableStateOf(false) }
    var showClearNextDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放队列") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.skipToPrevious() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "上一首")
                    }
                    IconButton(onClick = { viewModel.togglePlayPause() }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "播放/暂停")
                    }
                    IconButton(onClick = { viewModel.skipToNext() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "下一首")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 当前播放
            item {
                Text(
                    "正在播放",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)
                )
            }
            item {
                if (currentSong == null) {
                    EmptyQueueItem("暂无正在播放的歌曲")
                } else {
                    CurrentSongCard(currentSong!!)
                }
            }

            // Next 队列
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 16.dp, 16.dp, 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "下一首优先",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (nextQueue.isNotEmpty()) {
                        TextButton(onClick = { showClearNextDialog = true }) {
                            Text("清空")
                        }
                    }
                }
            }
            itemsIndexed(nextQueue, key = { _, song -> "next-${song.id}" }) { index, song ->
                QueueSongItem(
                    song = song,
                    index = index,
                    isCurrent = false,
                    showReorder = false,
                    onClick = { viewModel.playSong(song) },
                    onRemove = { viewModel.removeFromQueue(song) },
                    onPlayNext = { viewModel.playNext(song) },
                    onMoveUp = {},
                    onMoveDown = {}
                )
            }
            if (nextQueue.isEmpty()) {
                item { EmptyQueueItem("没有插队的歌曲") }
            }

            // 主队列
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 16.dp, 16.dp, 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "主队列 (${mainQueue.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (mainQueue.isNotEmpty()) {
                        TextButton(onClick = { showClearMainDialog = true }) {
                            Text("清空")
                        }
                    }
                }
            }
            itemsIndexed(mainQueue, key = { _, song -> "main-${song.id}" }) { index, song ->
                QueueSongItem(
                    song = song,
                    index = index,
                    isCurrent = song.id == currentSong?.id,
                    showReorder = true,
                    onClick = { viewModel.playSong(song) },
                    onRemove = { viewModel.removeFromQueue(song) },
                    onPlayNext = { viewModel.playNext(song) },
                    onMoveUp = { if (index > 0) viewModel.moveMainQueueItem(index, index - 1) },
                    onMoveDown = { if (index < mainQueue.lastIndex) viewModel.moveMainQueueItem(index, index + 1) }
                )
            }
            if (mainQueue.isEmpty()) {
                item { EmptyQueueItem("主队列 empty,可从主页添加歌曲") }
            }
        }
    }

    if (showClearNextDialog) {
        AlertDialog(
            onDismissRequest = { showClearNextDialog = false },
            confirmButton = {
                TextButton(onClick = { viewModel.clearNextQueue(); showClearNextDialog = false }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearNextDialog = false }) { Text("取消") }
            },
            title = { Text("清空下一首队列") },
            text = { Text("确定要清空所有插队歌曲吗?") }
        )
    }

    if (showClearMainDialog) {
        AlertDialog(
            onDismissRequest = { showClearMainDialog = false },
            confirmButton = {
                TextButton(onClick = { viewModel.clearMainQueue(); showClearMainDialog = false }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMainDialog = false }) { Text("取消") }
            },
            title = { Text("清空主队列") },
            text = { Text("确定要清空主队列吗?当前歌曲保留。") }
        )
    }
}

@Composable
private fun CurrentSongCard(song: Song) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text("${song.artist} · ${song.format} · ${formatDuration(song.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun QueueSongItem(
    song: Song,
    index: Int,
    isCurrent: Boolean,
    showReorder: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onPlayNext: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    ListItem(
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                "${song.artist} · ${formatDuration(song.durationMs)}",
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showReorder) {
                    IconButton(onClick = onMoveUp, enabled = index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                    }
                    IconButton(onClick = onMoveDown, enabled = index >= 0) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                    }
                }
                IconButton(onClick = onPlayNext) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = "下一首播放")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "移除")
                }
            }
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
    )
}

@Composable
private fun EmptyQueueItem(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
