package com.lossless.music.ui.download

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * B站下载页。
 *
 * 三个区域:
 *  1. 登录状态栏(未登录→点击登录,已登录→显示UID/退出)
 *  2. 链接输入栏(粘贴BV号/链接 → 解析 → 下载)
 *  3. 下载任务列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onBack: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val loginUid by viewModel.loginUid.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val inputUrl by viewModel.inputUrl.collectAsStateWithLifecycle()
    val parseResult by viewModel.parseResult.collectAsStateWithLifecycle()
    val isParsing by viewModel.isParsing.collectAsStateWithLifecycle()
    val showWebView by viewModel.showWebView.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("B站下载") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- 1. 登录状态 ----
            LoginStatusCard(
                isLoggedIn = isLoggedIn,
                uid = loginUid,
                onLogin = viewModel::showLoginWebView,
                onLogout = viewModel::logout
            )

            HorizontalDivider()

            // ---- 2. 链接输入 ----
            Text("下载音频", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = inputUrl,
                onValueChange = viewModel::onUrlChange,
                label = { Text("粘贴B站视频链接或BV号") },
                placeholder = { Text("https://www.bilibili.com/video/BVxxxxx") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (inputUrl.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onUrlChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清空")
                        }
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val msg = viewModel.parseVideo()
                            snackbarHost.showSnackbar(msg)
                        }
                    },
                    enabled = inputUrl.isNotBlank() && !isParsing
                ) {
                    if (isParsing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isParsing) "解析中" else "解析视频")
                }

                if (parseResult != null) {
                    Button(
                        onClick = {
                            scope.launch {
                                val msg = viewModel.startDownload()
                                snackbarHost.showSnackbar(msg)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) { Text("开始下载") }
                }
            }

            // 解析结果预览
            parseResult?.let { info ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(info.title, fontWeight = FontWeight.Medium, maxLines = 2)
                        Text(
                            "时长: ${info.durationText}  ·  ${if (info.isHighQuality) "高清" else "普通"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()

            // ---- 3. 下载任务列表 ----
            Text("下载列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("暂无下载任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(tasks) { task ->
                        DownloadTaskItem(task)
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // B站登录 WebView
    if (showWebView) {
        BiliLoginWebView(
            onLoginSuccess = {
                viewModel.onLoginSuccess()
                viewModel.hideLoginWebView()
            },
            onDismiss = viewModel::hideLoginWebView
        )
    }
}

@Composable
private fun LoginStatusCard(
    isLoggedIn: Boolean,
    uid: String,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isLoggedIn) Icons.Default.CheckCircle else Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = if (isLoggedIn) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(if (isLoggedIn) "已登录" else "未登录", fontWeight = FontWeight.Medium)
                    if (isLoggedIn && uid.isNotEmpty()) {
                        Text("UID: $uid", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("登录后可下载高清/会员视频",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton(onClick = if (isLoggedIn) onLogout else onLogin) {
                Text(if (isLoggedIn) "退出登录" else "登录B站")
            }
        }
    }
}

@Composable
private fun DownloadTaskItem(task: DownloadUiModel) {
    ListItem(
        headlineContent = { Text(task.title, maxLines = 1, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Column {
                Text(task.statusText, style = MaterialTheme.typography.bodySmall)
                if (task.status == DownloadStatusUi.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { task.progress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
        },
        leadingContent = {
            Icon(
                when (task.status) {
                    DownloadStatusUi.COMPLETED -> Icons.Default.CheckCircle
                    DownloadStatusUi.FAILED -> Icons.Default.Error
                    DownloadStatusUi.DOWNLOADING -> Icons.Default.Downloading
                    else -> Icons.Default.Schedule
                },
                contentDescription = null,
                tint = when (task.status) {
                    DownloadStatusUi.COMPLETED -> MaterialTheme.colorScheme.primary
                    DownloadStatusUi.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    )
}

// ==================== B站登录 WebView ====================

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BiliLoginWebView(
    onLoginSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = { Text("登录B站") },
        text = {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // 登录成功后URL会跳转到首页或带DedeUserID的cookie
                                val cookies = CookieManager.getInstance().getCookie(url)
                                if (cookies?.contains("DedeUserID") == true) {
                                    onLoginSuccess()
                                }
                            }
                        }
                        loadUrl("https://passport.bilibili.com/login")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(400.dp)
            )
        }
    )
}
