package com.lossless.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lossless.music.ui.navigation.AppNavHost
import com.lossless.music.ui.theme.LosslessMusicTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 应用唯一 Activity。
 *
 * 职责:
 *  - 启动时检查并请求存储权限(扫描外部音乐目录必需)
 *  - Android 11+ 引导用户授予 MANAGE_EXTERNAL_STORAGE
 *  - Android 10 及以下请求 READ_EXTERNAL_STORAGE
 *  - 权限通过后才进入主界面
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LosslessMusicTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PermissionGate {
                        AppNavHost()
                    }
                }
            }
        }
    }
}

/**
 * 权限门控:未授权时显示请求页,授权后显示正式内容。
 */
@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(checkStoragePermission(context)) }

    // Android 11+: 跳转"所有文件访问权限"设置页
    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = checkStoragePermission(context)
    }

    // Android 10 及以下: 运行时权限弹窗
    val readExtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.any { it } || checkStoragePermission(context)
    }

    if (hasPermission) {
        content()
    } else {
        PermissionRequestScreen(
            onRequestPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                    ).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    manageStorageLauncher.launch(intent)
                } else {
                    readExtLauncher.launch(
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    )
                }
            }
        )
    }
}

/**
 * 权限请求引导页。
 */
@Composable
private fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("需要存储权限", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Text(
                "本应用需要访问存储以扫描 /sdcard/Music/MP4_Music/ 下的音乐文件。\n" +
                    "请在接下来的设置中授予「所有文件访问权限」。",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequestPermission) {
                Text("授予权限")
            }
        }
    }
}

/**
 * 检查存储权限是否已授予。
 * - Android 11+: Environment.isExternalStorageManager()
 * - Android 10 及以下: READ_EXTERNAL_STORAGE 已授权
 */
private fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }
}
