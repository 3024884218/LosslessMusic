package com.lossless.music.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * songs 表 —— 对应一首已导入的歌曲。
 * 字段与 domain.model.Song 一一对应,但使用 Room 注解。
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "album") val album: String,
    @ColumnInfo(name = "year") val year: Int = 0,
    @ColumnInfo(name = "genre") val genre: String = "",
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "folder_path") val folderPath: String,
    @ColumnInfo(name = "format") val format: String,
    @ColumnInfo(name = "sample_rate") val sampleRate: Int,
    @ColumnInfo(name = "bit_depth") val bitDepth: Int,
    @ColumnInfo(name = "bit_rate") val bitRate: Int,
    @ColumnInfo(name = "channels") val channels: Int,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "cover_uri") val coverUri: String? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long = 0,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    /** 断点续播:上次播放位置(毫秒),0 表示从头开始 */
    @ColumnInfo(name = "resume_position") val resumePosition: Long = 0
)

/**
 * playlists 表 —— 用户手动创建的歌单。
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * playlist_song 关联表 —— 歌单与歌曲多对多关系。
 */
@Entity(
    tableName = "playlist_song",
    primaryKeys = ["playlist_id", "song_id"]
)
data class PlaylistSongEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis()
)

/**
 * 播放历史表 —— 记录每次播放(同一首歌可多次记录)。
 * 用于"最近播放"和播放统计。
 */
@Entity(tableName = "play_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "played_at") val playedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "played_duration") val playedDuration: Long = 0  // 实际播放时长(毫秒)
)
