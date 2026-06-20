package com.lossless.music.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 设置页。
 *
 * 包含:
 *  - 音质偏好(Hi-Res 输出开关)
 *  - 下载设置(仅 Wi-Fi 下载、自动导入)
 *  - 缓存管理(显示大小、清除缓存)
 *  - 关于
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBiliLogin: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val hiRes by viewModel.hiRes.collectAsStateWithLifecycle()
    val onlyWifi by viewModel.onlyWifi.collectAsStateWithLifecycle()
    val autoImport by viewModel.autoImport.collectAsStateWithLifecycle()
    val cacheSize by viewModel.cacheSize.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Toast 提示
    LaunchedEffect(toast) {
        toast?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            item { SettingsHeader("音质") }
            item {
                SettingsSwitchItem(
                    title = "Hi-Res 输出",
                    subtitle = "尽量以原始采样率输出(依赖设备支持)",
                    icon = Icons.Default.HighQuality,
                    checked = hiRes,
                    onCheckedChange = { viewModel.setHiRes(it) }
                )
            }

            item { SettingsHeader("下载与导入") }
            item {
                SettingsSwitchItem(
                    title = "仅 Wi-Fi 下载",
                    subtitle = "避免移动数据流量",
                    icon = Icons.Default.Wifi,
                    checked = onlyWifi,
                    onCheckedChange = { viewModel.setOnlyWifi(it) }
                )
            }
            item {
                SettingsSwitchItem(
                    title = "启动时自动导入内置音乐",
                    subtitle = "每次打开 App 扫描 assets/music 目录",
                    icon = Icons.Default.Sync,
                    checked = autoImport,
                    onCheckedChange = { viewModel.setAutoImport(it) }
                )
            }
            item {
                SettingsClickItem(
                    title = "B站 Cookie 管理",
                    subtitle = "重新登录或清除登录状态",
                    icon = Icons.Default.Cookie,
                    onClick = onOpenBiliLogin
                )
            }

            item { SettingsHeader("缓存") }
            item {
                SettingsClickItem(
                    title = "清除缓存",
                    subtitle = "当前缓存: $cacheSize",
                    icon = Icons.Default.DeleteSweep,
                    onClick = { viewModel.clearCache() }
                )
            }

            item { SettingsHeader("关于") }
            item {
                SettingsClickItem(
                    title = "无损音乐播放器",
                    subtitle = "版本 1.0.0 · 基于 Kotlin + Media3",
                    icon = Icons.Default.Info,
                    onClick = { }
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
private fun SettingsClickItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
