package com.lossless.music.ui.equalizer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 均衡器页。
 *
 * 5 段 EQ 滑动条 + 响度增益开关。
 * 默认关闭,保证无损音质;用户自行开启调节。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel()
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val gains by viewModel.gains.collectAsStateWithLifecycle()
    val loudness by viewModel.loudness.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("均衡器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 开关
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("启用均衡器", fontWeight = FontWeight.Bold)
                        Text("默认关闭,开启后会影响原始音质", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = enabled, onCheckedChange = { viewModel.setEnabled(it) })
                }
            }

            Spacer(Modifier.height(16.dp))

            // 5 段 EQ
            Text("5 段均衡", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            gains.forEachIndexed { index, gain ->
                EQBandSlider(
                    label = viewModel.bandLabels[index],
                    value = gain,
                    onValueChange = { viewModel.setGain(index, it) },
                    enabled = enabled
                )
            }

            Spacer(Modifier.height(16.dp))

            // 响度增益
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("响度增益", fontWeight = FontWeight.Bold)
                    Text("${loudness / 100} dB", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = loudness.toFloat(),
                        onValueChange = { viewModel.setLoudness(it.toInt()) },
                        valueRange = -2000f..6000f,
                        enabled = enabled,
                        steps = 80
                    )
                    Text("-20dB 到 +6dB", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun EQBandSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${String.format("%.1f", value)} dB", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -15f..15f,
            enabled = enabled,
            steps = 30
        )
    }
}
