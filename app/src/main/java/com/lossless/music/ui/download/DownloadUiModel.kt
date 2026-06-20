package com.lossless.music.ui.download

/** UI层用的下载状态 */
enum class DownloadStatusUi {
    PENDING, DOWNLOADING, COMPLETED, FAILED
}

/** 解析后的视频信息(预览) */
data class VideoInfoUi(
    val title: String,
    val durationText: String,
    val isHighQuality: Boolean,
    val bvId: String = "",
    val cid: Long = 0
)

/** 下载任务(UI层) */
data class DownloadUiModel(
    val id: Long,
    val title: String,
    val status: DownloadStatusUi,
    val progress: Int,
    val statusText: String
)
