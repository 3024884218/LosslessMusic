package com.lossless.music.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import kotlinx.coroutines.launch

/**
 * 主界面 —— 顶部栏 + 内容区 + 底部导航(仅一套)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: () -> Unit,
    onAddBiliClick: () -> Unit,
    onFolderClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onBrowserClick: () -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    // SAF 文件选择器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val n = viewModel.importUris(uris)
                snackbarHost.showSnackbar(if (n > 0) "成功导入 $n 首" else "导入失败")
            }
        }
    }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("无损音乐") },
                actions = {
                    IconButton(onClick = onBrowserClick) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "文件浏览器")
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "导入文件")
                    }
                    IconButton(onClick = onAddBiliClick) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "B站下载")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val tabs = listOf("全部", "最近", "文件夹", "艺术家", "歌单", "收藏")
                val icons = listOf(
                    Icons.Default.MusicNote,
                    Icons.Default.History,
                    Icons.Default.Folder,
                    Icons.Default.Person,
                    Icons.AutoMirrored.Filled.QueueMusic,
                    Icons.Default.Favorite
                )
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icons[index], contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> AllSongsTab(viewModel) { song -> viewModel.playSong(song); onNavigateToPlayer() }
                1 -> RecentTab(viewModel) { song -> viewModel.playSong(song); onNavigateToPlayer() }
                2 -> FoldersTab(viewModel, onFolderClick)
                3 -> ArtistsTab(viewModel, onArtistClick)
                4 -> PlaylistsTab(viewModel, onPlaylistClick, onCreatePlaylist = { showCreatePlaylistDialog = true })
                5 -> FavoritesTab(viewModel) { song -> viewModel.playSong(song); onNavigateToPlayer() }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name ->
                scope.launch {
                    viewModel.createPlaylist(name)
                    showCreatePlaylistDialog = false
                    snackbarHost.showSnackbar("歌单「$name」已创建")
                }
            }
        )
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建歌单") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("歌单名称") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AllSongsTab(viewModel: HomeViewModel, onSongClick: (Song) -> Unit) {
    val songs by viewModel.allSongs.collectAsStateWithLifecycle(initialValue = emptyList())
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    var renamingSong by remember { mutableStateOf<Song?>(null) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = viewModel::onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("搜索标题/艺术家/专辑") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )
        if (songs.isEmpty()) EmptyState("还没有歌曲,点击右上角导入")
        else LazyColumn {
            items(songs, key = { it.id }) { s ->
                SongItem(s, { onSongClick(s) }, { viewModel.toggleFavorite(s) }) { renamingSong = s }
            }
        }
    }
    renamingSong?.let { song ->
        RenameSongDialog(song.title, onDismiss = { renamingSong = null }) { newName ->
            viewModel.renameSong(song, newName)
            renamingSong = null
        }
    }
}

@Composable
private fun RecentTab(viewModel: HomeViewModel, onSongClick: (Song) -> Unit) {
    val recent by viewModel.recentPlayed.collectAsStateWithLifecycle(initialValue = emptyList())
    var renamingSong by remember { mutableStateOf<Song?>(null) }
    if (recent.isEmpty()) EmptyState("还没有播放记录")
    else LazyColumn {
        items(recent, key = { it.id }) { s ->
            SongItem(s, { onSongClick(s) }, { viewModel.toggleFavorite(s) }) { renamingSong = s }
        }
    }
    renamingSong?.let { song ->
        RenameSongDialog(song.title, onDismiss = { renamingSong = null }) { newName ->
            viewModel.renameSong(song, newName)
            renamingSong = null
        }
    }
}

@Composable
private fun FoldersTab(viewModel: HomeViewModel, onFolderClick: (String) -> Unit) {
    val folders by viewModel.folders.collectAsStateWithLifecycle(initialValue = emptyList())
    if (folders.isEmpty()) EmptyState("暂无文件夹")
    else LazyColumn {
        items(folders) { f ->
            ListItem(
                headlineContent = { Text(f.folderPath) },
                supportingContent = { Text("${f.songCount} 首") },
                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                modifier = Modifier.clickable { onFolderClick(f.folderPath) }
            ); HorizontalDivider()
        }
    }
}

@Composable
private fun ArtistsTab(viewModel: HomeViewModel, onArtistClick: (String) -> Unit) {
    val artists by viewModel.artists.collectAsStateWithLifecycle(initialValue = emptyList())
    if (artists.isEmpty()) EmptyState("暂无艺术家")
    else LazyColumn {
        items(artists) { a ->
            ListItem(
                headlineContent = { Text(a.artist) },
                supportingContent = { Text("${a.songCount} 首") },
                leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.clickable { onArtistClick(a.artist) }
            ); HorizontalDivider()
        }
    }
}

@Composable
private fun PlaylistsTab(viewModel: HomeViewModel, onPlaylistClick: (Long) -> Unit, onCreatePlaylist: () -> Unit) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    Column(Modifier.fillMaxSize()) {
        Button(onClick = onCreatePlaylist, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("新建歌单")
        }
        if (playlists.isEmpty()) EmptyState("还没有歌单,点击上方新建")
        else LazyColumn {
            items(playlists) { p ->
                ListItem(
                    headlineContent = { Text(p.name) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                    modifier = Modifier.clickable { onPlaylistClick(p.id) }
                ); HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FavoritesTab(viewModel: HomeViewModel, onSongClick: (Song) -> Unit) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    var renamingSong by remember { mutableStateOf<Song?>(null) }
    if (favorites.isEmpty()) EmptyState("还没有收藏的歌曲")
    else LazyColumn {
        items(favorites, key = { it.id }) { s ->
            SongItem(s, { onSongClick(s) }, { viewModel.toggleFavorite(s) }) { renamingSong = s }
        }
    }
    renamingSong?.let { song ->
        RenameSongDialog(song.title, onDismiss = { renamingSong = null }) { newName ->
            viewModel.renameSong(song, newName)
            renamingSong = null
        }
    }
}

@Composable
private fun SongItem(song: Song, onClick: () -> Unit, onToggleFavorite: () -> Unit, onRename: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
        supportingContent = { Text("${song.artist} · ${formatDuration(song.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(song.qualityBadge, style = MaterialTheme.typography.labelSmall) }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { showMenu = false; onRename() }
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}

/**
 * 重命名对话框(通用)。
 * @param currentTitle 当前标题(预填)
 * @param onDismiss 取消
 * @param onConfirm 确认新标题
 */
@Composable
fun RenameSongDialog(currentTitle: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("歌曲名称") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank() && name.trim() != currentTitle) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun formatDuration(ms: Long): String {
    val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60)
}
