package com.lossless.music.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lossless.music.domain.model.Song

/**
 * SongListRoute:连接 ViewModel 与 SongListScreen。
 * 在 NavHost 中统一调用,根据路由参数自动加载对应数据。
 */
@Composable
fun SongListRoute(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    viewModel: SongListViewModel = hiltViewModel()
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    var renamingSong by remember { mutableStateOf<Song?>(null) }
    SongListScreen(
        title = viewModel.title,
        songs = songs,
        onBack = onBack,
        onSongClick = { song ->
            viewModel.playSong(song)
            onNavigateToPlayer()
        },
        onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
        onRename = { song -> renamingSong = song }
    )
    renamingSong?.let { song ->
        RenameSongDialog(song.title, onDismiss = { renamingSong = null }) { newName ->
            viewModel.renameSong(song, newName)
            renamingSong = null
        }
    }
}

/**
 * 通用歌曲列表页(文件夹/艺术家/歌单详情共用)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListScreen(
    title: String,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    songs: List<Song>,
    onToggleFavorite: (Song) -> Unit,
    onRename: (Song) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (songs.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无歌曲", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(songs, key = { it.id }) { song ->
                    var showMenu by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = {
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            Text("${song.artist} · ${formatDur(song.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(song.qualityBadge, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onToggleFavorite(song) }) {
                                    Icon(
                                        if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "收藏",
                                        tint = if (song.isFavorite) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("重命名") },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                        onClick = { showMenu = false; onRename(song) }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable { onSongClick(song) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun formatDur(ms: Long): String {
    val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60)
}
