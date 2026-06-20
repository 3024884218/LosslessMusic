package com.lossless.music.domain.model

/**
 * 音频文件实体(领域模型)。
 * 对应 Room 表 songs,记录一首已导入到 App 私有目录的歌曲的完整信息。
 *
 * 音质相关字段保留原始技术参数,播放时据此确保不重采样。
 */
data class Song(
    val id: Long = 0,
    val title: String,              // 标题(优先ID3,否则文件名)
    val artist: String,             // 艺术家
    val album: String,              // 专辑
    val year: Int = 0,              // 年份
    val genre: String = "",         // 流派
    val durationMs: Long,           // 时长(毫秒)
    val filePath: String,           // App私有目录内的相对路径
    val folderPath: String,         // 来源文件夹(用于"文件夹"分类)
    val format: String,             // 格式 mp3/flac/wav...
    val sampleRate: Int,            // 采样率 Hz
    val bitDepth: Int,              // 位深(FLAC/WAV有,MP3为0)
    val bitRate: Int,               // 比特率 kbps
    val channels: Int,              // 声道数
    val fileSize: Long,             // 文件大小(字节)
    val coverUri: String? = null,   // 内嵌封面提取后的缓存路径
    val addedAt: Long = System.currentTimeMillis(),  // 导入时间戳
    val lastPlayedAt: Long = 0,     // 最近播放时间戳
    val playCount: Int = 0,         // 播放次数
    val isFavorite: Boolean = false,// 特别收藏
    val resumePosition: Long = 0    // 断点续播位置(毫秒)
) {
    /** 是否高解析音频(Hi-Res):24bit 或采样率>=96kHz */
    val isHiRes: Boolean
        get() = bitDepth >= 24 || sampleRate >= 96000

    /** 用于显示的音质徽章文案 */
    val qualityBadge: String
        get() = when {
            isHiRes -> "Hi-Res"
            format.equals("flac", true) -> "FLAC"
            bitRate >= 320 -> "320k"
            bitRate >= 256 -> "256k"
            else -> format.uppercase()
        }
}
