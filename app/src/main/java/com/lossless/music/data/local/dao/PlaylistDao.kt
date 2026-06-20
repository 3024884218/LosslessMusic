package com.lossless.music.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.lossless.music.data.local.entity.PlaylistEntity
import com.lossless.music.data.local.entity.PlaylistSongEntity
import com.lossless.music.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

/**
 * 歌单相关 DAO。
 */
@Dao
interface PlaylistDao {

    // ---- 歌单 CRUD ----

    @Query("SELECT * FROM playlists ORDER BY created_at DESC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylist(playlistId: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    // ---- 歌单-歌曲 关联 ----

    /** 添加歌曲到歌单末尾 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongEntity)

    /** 批量添加 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongsToPlaylist(items: List<PlaylistSongEntity>)

    /** 获取歌单内下一个可用 position */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_song WHERE playlist_id = :playlistId")
    suspend fun getNextPosition(playlistId: Long): Int

    /** 查询歌单内所有歌曲(带歌曲完整信息) */
    @Transaction
    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_song ps ON s.id = ps.song_id
        WHERE ps.playlist_id = :playlistId
        ORDER BY ps.position
    """)
    fun observeSongsInPlaylist(playlistId: Long): Flow<List<SongEntity>>

    /** 从歌单移除歌曲 */
    @Query("DELETE FROM playlist_song WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun removeFromPlaylist(playlistId: Long, songId: Long)

    /** 更新歌曲在歌单内的位置(拖拽排序) */
    @Query("UPDATE playlist_song SET position = :position WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun updatePosition(playlistId: Long, songId: Long, position: Int)

    /** 清空歌单 */
    @Query("DELETE FROM playlist_song WHERE playlist_id = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)
}
