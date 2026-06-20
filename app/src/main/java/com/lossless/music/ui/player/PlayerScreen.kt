package com.lossless.music.ui.player

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lossless.music.domain.model.PlayMode
import kotlinx.coroutines.delay

/**
 * 全屏播放页。
 * 从 PlayerViewModel 观察:当前歌曲、播放状态、进度、播放模式、歌词。
 * 控制按钮调用 PlayerController 转发给 PlaybackService。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onQueueClick: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val song by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val position by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val playMode by viewModel.playMode.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val currentLyricIndex by viewModel.currentLyricIndex.collectAsStateWithLifecycle()
    var showLyrics by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var remainingMs by remember { mutableStateOf(0L) }
    val context = LocalContext.current

    // 睡眠定时器剩余时间刷新
    LaunchedEffect(showSleepDialog, isPlaying) {
        while (true) {
            remainingMs = viewModel.getSleepTimerRemainingMs()
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("正在播放") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowDropDown, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = { showSleepDialog = true }) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = "睡眠定时器",
                            tint = if (remainingMs > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            Icons.Default.Subtitles,
                            contentDescription = if (showLyrics) "封面" else "歌词",
                            tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onQueueClick) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "队列")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 上:封面或歌词
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics) {
                    LyricsPanel(lyrics, currentLyricIndex)
                } else {
                    CoverPanel(song)
                }
            }

            // 中:歌曲信息 + 进度条
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    song?.title ?: "未在播放",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    song?.artist ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (song != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${song!!.qualityBadge} · ${song!!.sampleRate / 1000}kHz · ${if (song!!.bitDepth > 0) "${song!!.bitDepth}bit" else "有损"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (remainingMs > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "睡眠定时: ${formatTime(remainingMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = if (duration > 0) position.toFloat() / duration else 0f,
                    onValueChange = { v ->
                        if (duration > 0) viewModel.seekTo((v * duration).toLong())
                    },
                    enabled = song != null
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), style = MaterialTheme.typography.labelSmall)
                    Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
                }
            }

            // 下:控制按钮
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.cyclePlayMode() }) {
                    Icon(
                        when (playMode) {
                            PlayMode.SEQUENCE -> Icons.Default.Repeat
                            PlayMode.REPEAT_ALL -> Icons.Default.Repeat
                            PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                            PlayMode.SHUFFLE -> Icons.Default.Shuffle
                        },
                        contentDescription = "播放模式",
                        tint = if (playMode != PlayMode.SEQUENCE) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.skipToPrevious() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(40.dp))
                }
                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放/暂停",
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { viewModel.skipToNext() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { viewModel.toggleFavorite() }) {
                    Icon(
                        if (song?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (song?.isFavorite == true) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            remainingMs = remainingMs,
            onDismiss = { showSleepDialog = false },
            onStart = { minutes ->
                viewModel.startSleepTimer(minutes)
                Toast.makeText(context, "已设置 ${minutes} 分钟后停止", Toast.LENGTH_SHORT).show()
                showSleepDialog = false
            },
            onCancel = {
                viewModel.cancelSleepTimer()
                Toast.makeText(context, "已取消睡眠定时", Toast.LENGTH_SHORT).show()
                showSleepDialog = false
            }
        )
    }
}

@Composable
private fun SleepTimerDialog(
    remainingMs: Long,
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = { Text("睡眠定时器") },
        text = {
            Column {
                if (remainingMs > 0) {
                    Text("剩余: ${formatTime(remainingMs)}", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                }
                val options = listOf(15, 30, 45, 60, 90)
                options.forEach { min ->
                    TextButton(
                        onClick = { onStart(min) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${min} 分钟")
                    }
                }
                if (remainingMs > 0) {
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("取消定时", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    )
}

@Composable
private fun CoverPanel(song: com.lossless.music.domain.model.Song?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.MusicNote, contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun LyricsPanel(lyrics: List<com.lossless.music.ui.lyrics.LrcLine>, currentIndex: Int) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex.coerceAtMost(lyrics.lastIndex), -100)
        }
    }
    if (lyrics.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌词", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 120.dp)
    ) {
        itemsIndexed(lyrics, key = { index, _ -> index }) { index, line ->
            val isCurrent = index == currentIndex
            Text(
                line.text,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                style = if (isCurrent) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                else MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60)
}
