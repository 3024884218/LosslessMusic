package com.lossless.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lossless.music.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 播放历史 DAO。
 * 记录每次播放,支持"最近播放"和统计。
 */
@Dao
interface HistoryDao {

    /** 记录一次播放 */
    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    /** 最近播放的歌曲(去重,取每首歌最后一次播放时间) */
    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN (
            SELECT song_id, MAX(played_at) as last_play
            FROM play_history
            GROUP BY song_id
        ) h ON s.id = h.song_id
        ORDER BY h.last_play DESC
        LIMIT :limit
    """)
    fun observeRecentPlayed(limit: Int = 50): Flow<List<com.lossless.music.data.local.entity.SongEntity>>

    /** 播放次数 Top N */
    @Query("""
        SELECT * FROM songs
        WHERE play_count > 0
        ORDER BY play_count DESC, last_played_at DESC
        LIMIT :limit
    """)
    fun observeTopPlayed(limit: Int = 50): Flow<List<com.lossless.music.data.local.entity.SongEntity>>

    /** 从未播放的歌曲 */
    @Query("SELECT * FROM songs WHERE play_count = 0 ORDER BY added_at DESC")
    fun observeNeverPlayed(): Flow<List<com.lossless.music.data.local.entity.SongEntity>>

    /** 清空历史(保留 play_count) */
    @Query("DELETE FROM play_history")
    suspend fun clearHistory()

    /** 删除指定歌曲的历史 */
    @Query("DELETE FROM play_history WHERE song_id = :songId")
    suspend fun deleteBySong(songId: Long)
}
