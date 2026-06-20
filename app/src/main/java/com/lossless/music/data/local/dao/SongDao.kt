package com.lossless.music.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lossless.music.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

/**
 * songs 表的数据访问对象。
 * 所有查询返回 Flow,UI 层可响应式观察数据变化。
 */
@Dao
interface SongDao {

    // ---- 查询 ----

    /** 全部歌曲(按添加时间倒序,最新导入在前) */
    @Query("SELECT * FROM songs ORDER BY added_at DESC")
    fun observeAllSongs(): Flow<List<SongEntity>>

    /** 全部歌曲(一次性快照,导入时用) */
    @Query("SELECT * FROM songs ORDER BY added_at DESC")
    suspend fun getAllSongs(): List<SongEntity>

    /** 收藏歌曲 */
    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY last_played_at DESC")
    fun observeFavorites(): Flow<List<SongEntity>>

    /** 按文件夹分组统计(用于"文件夹"分类) */
    @Query("SELECT folder_path, COUNT(*) as song_count FROM songs GROUP BY folder_path ORDER BY folder_path")
    fun observeFolders(): Flow<List<FolderCount>>

    /** 某文件夹下的歌曲 */
    @Query("SELECT * FROM songs WHERE folder_path = :folder ORDER BY title")
    fun observeSongsByFolder(folder: String): Flow<List<SongEntity>>

    /** 按艺术家分组(用于"艺术家"分类) */
    @Query("SELECT artist, COUNT(*) as song_count FROM songs GROUP BY artist ORDER BY artist")
    fun observeArtists(): Flow<List<ArtistCount>>

    /** 某艺术家的歌曲 */
    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album, title")
    fun observeSongsByArtist(artist: String): Flow<List<SongEntity>>

    /** 按专辑分组 */
    @Query("SELECT album, artist, COUNT(*) as song_count FROM songs GROUP BY album ORDER BY album")
    fun observeAlbums(): Flow<List<AlbumCount>>

    /** 搜索(标题/艺术家/专辑模糊匹配) */
    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' ORDER BY title")
    fun search(query: String): Flow<List<SongEntity>>

    /** 单首歌曲 */
    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): SongEntity?

    /** 多首歌曲(用于队列/歌单) */
    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<Long>): List<SongEntity>

    /** 检查文件路径是否已存在(去重,避免重复导入) */
    @Query("SELECT COUNT(*) FROM songs WHERE file_path = :path")
    suspend fun countByPath(path: String): Int

    // ---- 插入/更新 ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>): List<Long>

    @Update
    suspend fun update(song: SongEntity)

    /** 切换收藏状态 */
    @Query("UPDATE songs SET is_favorite = :favorite WHERE id = :songId")
    suspend fun setFavorite(songId: Long, favorite: Boolean)

    /** 更新播放记录 */
    @Query("UPDATE songs SET last_played_at = :timestamp, play_count = play_count + 1 WHERE id = :songId")
    suspend fun recordPlay(songId: Long, timestamp: Long)

    /** 修改歌曲标题 */
    @Query("UPDATE songs SET title = :newTitle WHERE id = :songId")
    suspend fun renameSong(songId: Long, newTitle: String)

    /** 保存断点续播位置 */
    @Query("UPDATE songs SET resume_position = :position WHERE id = :songId")
    suspend fun saveResumePosition(songId: Long, position: Long)

    /** 清除断点续播位置(播完时调用) */
    @Query("UPDATE songs SET resume_position = 0 WHERE id = :songId")
    suspend fun clearResumePosition(songId: Long)

    /** B站下载的歌曲(智能歌单用) */
    @Query("SELECT * FROM songs WHERE folder_path = 'B站下载' ORDER BY added_at DESC")
    fun observeBiliDownloads(): Flow<List<SongEntity>>

    /** 最近 7 天播放过的歌曲 */
    @Query("""
        SELECT * FROM songs
        WHERE last_played_at > :sinceTimestamp
        ORDER BY last_played_at DESC
    """)
    fun observeRecentlyPlayedSince(sinceTimestamp: Long): Flow<List<SongEntity>>

    // ---- 删除 ----

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun delete(songId: Long)
}

/** 文件夹统计结果(配合 observeFolders) */
data class FolderCount(
    @ColumnInfo(name = "folder_path") val folderPath: String,
    @ColumnInfo(name = "song_count") val songCount: Int
)

/** 艺术家统计结果 */
data class ArtistCount(
    val artist: String,
    @ColumnInfo(name = "song_count") val songCount: Int
)

/** 专辑统计结果 */
data class AlbumCount(
    val album: String,
    val artist: String,
    @ColumnInfo(name = "song_count") val songCount: Int
)
