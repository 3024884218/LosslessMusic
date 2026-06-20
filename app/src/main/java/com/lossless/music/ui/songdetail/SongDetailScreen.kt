package com.lossless.music.ui.songdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 歌曲详情页:展示完整元数据 + 播放统计。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    songId: Long,
    onBack: () -> Unit,
    viewModel: SongDetailViewModel = hiltViewModel()
) {
    val song by viewModel.song.collectAsStateWithLifecycle()

    LaunchedEffect(songId) { viewModel.load(songId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歌曲详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        val s = song ?: run {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题
            Text(s.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(s.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.album.isNotBlank()) Text("专辑: ${s.album}", style = MaterialTheme.typography.bodyMedium)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // 音质信息
            SectionTitle("音频参数")
            InfoRow("格式", s.format.uppercase())
            InfoRow("采样率", "${s.sampleRate / 1000} kHz")
            InfoRow("位深", if (s.bitDepth > 0) "${s.bitDepth} bit" else "有损")
            InfoRow("比特率", "${s.bitRate} kbps")
            InfoRow("声道", if (s.channels == 2) "立体声" else "${s.channels} 声道")
            InfoRow("文件大小", formatSize(s.fileSize))
            InfoRow("时长", formatDur(s.durationMs))

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // 播放统计
            SectionTitle("播放统计")
            InfoRow("播放次数", "${s.playCount} 次")
            InfoRow("最近播放", if (s.lastPlayedAt > 0) formatTime(s.lastPlayedAt) else "未播放")
            InfoRow("添加时间", formatTime(s.addedAt))
            InfoRow("断点位置", if (s.resumePosition > 0) formatDur(s.resumePosition) else "无")

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // 文件路径
            SectionTitle("文件信息")
            InfoRow("文件路径", s.filePath)
            InfoRow("来源文件夹", s.folderPath)

            if (s.isFavorite) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("已收藏", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun formatDur(ms: Long): String {
    val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
