package com.lossless.music.domain.model

/**
 * 播放模式。
 * 顺序播放 / 随机播放 / 单曲循环 / 列表循环
 */
enum class PlayMode {
    SEQUENCE,   // 顺序播放:按队列顺序播完即止
    SHUFFLE,    // 随机播放:打乱顺序
    REPEAT_ONE, // 单曲循环:当前歌曲循环
    REPEAT_ALL  // 列表循环:列表播完从头开始
}

/**
 * B站下载任务状态。
 */
enum class DownloadStatus {
    PENDING,      // 等待中
    DOWNLOADING,  // 下载中
    EXTRACTING,   // 提取音频中
    COMPLETED,    // 完成
    FAILED        // 失败
}

/**
 * B站下载任务实体。
 */
data class DownloadTask(
    val id: Long = 0,
    val biliUrl: String,            // 原始B站链接
    val videoTitle: String,         // 视频标题
    val coverUrl: String? = null,   // 封面URL
    val durationMs: Long = 0,       // 时长
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0,          // 0-100
    val outputPath: String? = null, // 下载完成后音频文件路径
    val errorMsg: String? = null,   // 失败原因
    val createdAt: Long = System.currentTimeMillis()
)
