package com.lossless.music.ui.lyrics

import android.content.Context
import java.io.File
import java.util.regex.Pattern

/**
 * 歌词解析器。
 * 支持标准 LRC 格式:
 *  [mm:ss.xx]歌词内容
 *  [mm:ss:xx]歌词内容
 *  [mm:ss.xx]<mm:ss.xx>歌词内容(合并标签)
 *
 * 返回按时间排序的 (时间毫秒, 歌词行) 列表。
 */
object LrcParser {

    private val TIME_PATTERN = Pattern.compile("\\[(\\d{1,2}):([\\d.]+)\\]")

    fun parseFile(context: Context, filePath: String): List<LrcLine> {
        // 1. 尝试私有目录 fileDir/music/xxx.lrc
        val file = File(context.filesDir, "music/$filePath")
        if (file.exists()) return parseText(file.readText())
        // 2. 尝试 assets 目录 (路径换 .lrc)
        return try {
            val assetPath = "music/${filePath.substringBeforeLast('.')}.lrc"
            context.assets.open(assetPath).bufferedReader().use { it.readText() }.let(::parseText)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseText(text: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        text.lines().forEach { raw ->
            val matcher = TIME_PATTERN.matcher(raw)
            val times = mutableListOf<Long>()
            var end = 0
            while (matcher.find()) {
                val minute = matcher.group(1)?.toIntOrNull() ?: 0
                val second = matcher.group(2)?.toFloatOrNull() ?: 0f
                times.add((minute * 60 * 1000 + second * 1000).toLong())
                end = matcher.end()
            }
            val content = raw.substring(end).trim()
            times.forEach { time ->
                lines.add(LrcLine(time, content))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** 根据当前时间找到正在唱的歌词索引 */
    fun findCurrentIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var index = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs) index = i
            else break
        }
        return index
    }
}

/** 一行歌词 */
data class LrcLine(
    val timeMs: Long,
    val text: String
)
