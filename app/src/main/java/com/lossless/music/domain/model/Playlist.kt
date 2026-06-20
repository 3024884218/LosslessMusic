package com.lossless.music.domain.model

/**
 * 用户手动创建的歌单。
 */
data class Playlist(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val songCount: Int = 0  // 冗余字段,展示用,实际以关联表为准
)

/**
 * 歌单-歌曲 关联表。
 * position 用于歌单内排序(支持拖拽调整)。
 */
data class PlaylistSong(
    val id: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val position: Int,            // 歌单内位置
    val addedAt: Long = System.currentTimeMillis()
)
