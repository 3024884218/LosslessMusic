package com.lossless.music.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lossless.music.data.local.dao.HistoryDao
import com.lossless.music.data.local.dao.PlaylistDao
import com.lossless.music.data.local.dao.SongDao
import com.lossless.music.data.local.entity.HistoryEntity
import com.lossless.music.data.local.entity.PlaylistEntity
import com.lossless.music.data.local.entity.PlaylistSongEntity
import com.lossless.music.data.local.entity.SongEntity

/**
 * 应用主数据库。
 * 包含 songs(歌曲)、playlists(歌单)、playlist_song(歌单-歌曲关联)、play_history(播放历史) 四张表。
 *
 * 版本号变更会触发 fallbackToDestructiveMigration(见 DatabaseModule),
 * 开发阶段直接重建,正式版需迁移。
 */
@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        HistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val DB_NAME = "lossless_music.db"
    }
}
