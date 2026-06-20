package com.lossless.music.data.repository

import android.content.Context
import android.net.Uri
import com.lossless.music.data.local.dao.HistoryDao
import com.lossless.music.data.local.dao.PlaylistDao
import com.lossless.music.data.local.dao.SongDao
import com.lossless.music.data.local.entity.HistoryEntity
import com.lossless.music.data.local.entity.PlaylistEntity
import com.lossless.music.data.local.entity.PlaylistSongEntity
import com.lossless.music.data.local.entity.SongEntity
import com.lossless.music.data.scanner.AudioMetadata
import com.lossless.music.data.scanner.AssetScanner
import com.lossless.music.data.scanner.ExternalScanner
import com.lossless.music.data.scanner.MetadataReader
import com.lossless.music.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音乐数据仓库。
 *
 * 职责:
 *  1. 协调 SongDao / PlaylistDao / MetadataReader
 *  2. 把 Entity ↔ Domain Model 互转(隔离数据库细节)
 *  3. 提供音频导入流程(SAF Uri → 复制到私有目录 → 读元数据 → 入库)
 *
 * 所有方法在合适调度器上运行,UI 层无需关心线程。
 */
@Singleton
class MusicRepository @Inject constructor(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val historyDao: HistoryDao,
    private val metadataReader: MetadataReader,
    private val assetScanner: AssetScanner,
    private val externalScanner: ExternalScanner,
    @ApplicationContext private val context: Context
) {

    // 音乐文件存放根目录(App 私有目录)
    private val musicDir: File by lazy {
        File(context.filesDir, "music").apply { if (!exists()) mkdirs() }
    }
    // 封面缓存目录
    private val coverDir: File by lazy {
        File(context.cacheDir, "covers").apply { if (!exists()) mkdirs() }
    }

    // ==================== 观察 Flow ====================

    fun observeAllSongs(): Flow<List<Song>> =
        songDao.observeAllSongs().map { it.map(::toDomain) }

    fun observeFavorites(): Flow<List<Song>> =
        songDao.observeFavorites().map { it.map(::toDomain) }

    fun observeFolders() = songDao.observeFolders()

    fun observeSongsByFolder(folder: String): Flow<List<Song>> =
        songDao.observeSongsByFolder(folder).map { it.map(::toDomain) }

    fun observeArtists() = songDao.observeArtists()

    fun observeSongsByArtist(artist: String): Flow<List<Song>> =
        songDao.observeSongsByArtist(artist).map { it.map(::toDomain) }

    fun observeAlbums() = songDao.observeAlbums()

    fun search(query: String): Flow<List<Song>> =
        songDao.search(query).map { it.map(::toDomain) }

    fun observeAllPlaylists() = playlistDao.observeAllPlaylists()

    fun observeSongsInPlaylist(playlistId: Long): Flow<List<Song>> =
        playlistDao.observeSongsInPlaylist(playlistId).map { it.map(::toDomain) }

    // ==================== 单次查询 ====================

    suspend fun getSong(id: Long): Song? = withContext(Dispatchers.IO) {
        songDao.getSongById(id)?.let(::toDomain)
    }

    suspend fun getSongs(ids: List<Long>): List<Song> = withContext(Dispatchers.IO) {
        songDao.getSongsByIds(ids).map(::toDomain)
    }

    // ==================== 导入 ====================

    /**
     * 从 SAF Uri 导入音频文件。
     * 流程:
     *  1. 读取元数据(标题/格式等)
     *  2. 复制文件到 App 私有目录 music/<folder>/<filename>
     *  3. 提取内嵌封面
     *  4. 插入数据库
     *
     * @param sourceUri 用户在系统文件选择器选中的 Uri
     * @param sourceFolder 来源文件夹名(用于"文件夹"分类,如 "下载" / "周杰伦")
     * @return 导入后的 Song,失败返回 null
     */
    suspend fun importFromUri(sourceUri: Uri, sourceFolder: String = "导入"): Song? =
        withContext(Dispatchers.IO) {
            try {
                // 1. 取文件名
                val fileName = queryFileName(sourceUri) ?: return@withContext null

                // 2. 读元数据
                val meta = metadataReader.readFromUri(sourceUri, fileName)

                // 3. 目标目录
                val targetDir = File(musicDir, sourceFolder).apply { if (!exists()) mkdirs() }
                val targetFile = File(targetDir, fileName)

                // 去重:已存在同名文件则加序号
                val finalFile = ensureUniqueFile(targetFile)

                // 4. 复制文件(不转码,原样二进制复制,保证无损)
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    finalFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext null

                // 5. 提取封面
                val coverPath = metadataReader.extractCoverToFile(finalFile, coverDir)

                // 6. 入库
                val entity = SongEntity(
                    title = meta.title,
                    artist = meta.artist,
                    album = meta.album,
                    year = meta.year,
                    genre = meta.genre,
                    durationMs = meta.durationMs,
                    filePath = finalFile.relativeTo(musicDir).path,
                    folderPath = sourceFolder,
                    format = meta.format,
                    sampleRate = meta.sampleRate,
                    bitDepth = meta.bitDepth,
                    bitRate = meta.bitRate,
                    channels = meta.channels,
                    fileSize = finalFile.length(),
                    coverUri = coverPath
                )
                val id = songDao.insert(entity)
                toDomain(entity.copy(id = id))
            } catch (e: Exception) {
                null
            }
        }

    /**
     * 批量导入多个 Uri。
     * @return 成功导入的歌曲列表
     */
    suspend fun importMultiple(uris: List<Uri>, sourceFolder: String = "导入"): List<Song> =
        withContext(Dispatchers.IO) {
            uris.mapNotNull { importFromUri(it, sourceFolder) }
        }

    /**
     * 导入默认外部音乐目录 /sdcard/Music/MP4_Music/ 到数据库。
     * 不复制文件,只记录绝对路径,用于播放时直接读取外部文件。
     * @return 导入的歌曲数量
     */
    suspend fun importExternalMusicDir(): Int = withContext(Dispatchers.IO) {
        val songs = externalScanner.scanDefaultExternalMusicDir()
        if (songs.isEmpty()) return@withContext 0
        // 去重:已存在相同 filePath 的不插入
        val existing = songDao.getAllSongs().map { it.filePath }.toSet()
        val newSongs = songs.filter { it.filePath !in existing }
        if (newSongs.isEmpty()) return@withContext 0
        songDao.insertAll(newSongs)
        newSongs.size
    }

    /**
     * 从本地文件导入(B站下载等场景)。
     * 读取元数据并插入数据库。
     *
     * 注意：本方法假设 [file] 已经在 [musicDir] 下（App 私有目录），
     * filePath 字段会存相对路径。若文件在公共目录，请改用 [importFromPublicFile]。
     */
    suspend fun importFromFile(file: java.io.File, sourceFolder: String = "B站下载"): Song? =
        withContext(Dispatchers.IO) {
            try {
                val meta = metadataReader.readFromFile(file)
                val coverPath = metadataReader.extractCoverToFile(file, coverDir)
                val entity = SongEntity(
                    title = meta.title.ifBlank { file.nameWithoutExtension },
                    artist = meta.artist,
                    album = meta.album,
                    year = meta.year,
                    genre = meta.genre,
                    durationMs = meta.durationMs,
                    filePath = file.relativeTo(musicDir).path,
                    folderPath = sourceFolder,
                    format = meta.format,
                    sampleRate = meta.sampleRate,
                    bitDepth = meta.bitDepth,
                    bitRate = meta.bitRate,
                    channels = meta.channels,
                    fileSize = file.length(),
                    coverUri = coverPath
                )
                val id = songDao.insert(entity)
                toDomain(entity.copy(id = id))
            } catch (e: Exception) {
                null
            }
        }

    /**
     * 从公共音乐目录的文件导入（B站下载到 /sdcard/Music/LosslessMusic/... 后调用）。
     *
     * 与 [importFromFile] 的区别：
     *  - 不复制文件（文件已在公共目录）
     *  - filePath 存 `EXTERNAL:绝对路径`，PlaybackService 会识别此前缀直接 file:// 播放
     *  - 删除歌曲时会连同公共目录文件一起删除（见 [deleteSong]）
     *
     * @param file 公共目录中的音频文件
     * @param sourceFolder 用于"文件夹"分类的标签（如 "B站下载"）
     * @return 导入后的 Song，失败返回 null
     */
    suspend fun importFromPublicFile(file: java.io.File, sourceFolder: String = "B站下载"): Song? =
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists()) return@withContext null
                val meta = metadataReader.readFromFile(file)
                val coverPath = metadataReader.extractCoverToFile(file, coverDir)
                val entity = SongEntity(
                    title = meta.title.ifBlank { file.nameWithoutExtension },
                    artist = meta.artist,
                    album = meta.album,
                    year = meta.year,
                    genre = meta.genre,
                    durationMs = meta.durationMs,
                    // EXTERNAL: 前缀标识"外部公共目录绝对路径"
                    filePath = "EXTERNAL:${file.absolutePath}",
                    folderPath = sourceFolder,
                    format = meta.format,
                    sampleRate = meta.sampleRate,
                    bitDepth = meta.bitDepth,
                    bitRate = meta.bitRate,
                    channels = meta.channels,
                    fileSize = file.length(),
                    coverUri = coverPath
                )
                val id = songDao.insert(entity)
                toDomain(entity.copy(id = id))
            } catch (e: Exception) {
                null
            }
        }

    // ==================== 收藏 ====================

    suspend fun toggleFavorite(songId: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        songDao.setFavorite(songId, favorite)
    }

    // ==================== 重命名 ====================

    /** 修改歌曲标题(不影响文件,只改数据库记录) */
    suspend fun renameSong(songId: Long, newTitle: String) = withContext(Dispatchers.IO) {
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty()) {
            songDao.renameSong(songId, trimmed)
        }
    }

    // ==================== 播放记录 ====================

    suspend fun recordPlay(songId: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        songDao.recordPlay(songId, now)
        historyDao.insert(HistoryEntity(songId = songId, playedAt = now))
    }

    // ==================== 断点续播 ====================

    /** 保存播放位置(暂停/切歌时调用) */
    suspend fun saveResumePosition(songId: Long, positionMs: Long) = withContext(Dispatchers.IO) {
        songDao.saveResumePosition(songId, positionMs)
    }

    /** 清除断点(播完时调用) */
    suspend fun clearResumePosition(songId: Long) = withContext(Dispatchers.IO) {
        songDao.clearResumePosition(songId)
    }

    // ==================== 智能歌单 ====================

    /** 最近播放(去重,最近 50 首) */
    fun observeRecentPlayed() = historyDao.observeRecentPlayed(50).map { it.map(::toDomain) }

    /** 播放 Top 50 */
    fun observeTopPlayed() = historyDao.observeTopPlayed(50).map { it.map(::toDomain) }

    /** 从未播放 */
    fun observeNeverPlayed() = historyDao.observeNeverPlayed().map { it.map(::toDomain) }

    /** B站下载 */
    fun observeBiliDownloads() = songDao.observeBiliDownloads().map { it.map(::toDomain) }

    /** 最近 7 天播放 */
    fun observePlayedLast7Days() = songDao.observeRecentlyPlayedSince(
        System.currentTimeMillis() - 7L * 24 * 3600 * 1000
    ).map { it.map(::toDomain) }

    // ==================== 删除 ====================

    suspend fun deleteSong(songId: Long) = withContext(Dispatchers.IO) {
        val song = songDao.getSongById(songId) ?: return@withContext
        // 删除文件：识别 EXTERNAL: 前缀（公共目录绝对路径）vs 相对路径（私有目录）
        val file = if (song.filePath.startsWith("EXTERNAL:")) {
            java.io.File(song.filePath.removePrefix("EXTERNAL:"))
        } else {
            java.io.File(musicDir, song.filePath)
        }
        if (file.exists()) file.delete()
        // 删除封面
        song.coverUri?.let { File(it).delete() }
        // 删除数据库记录
        songDao.delete(songId)
    }

    // ==================== 歌单管理 ====================

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        val pl = playlistDao.getPlaylist(playlistId) ?: return@withContext
        playlistDao.updatePlaylist(pl.copy(name = newName))
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) = withContext(Dispatchers.IO) {
        var pos = playlistDao.getNextPosition(playlistId)
        val items = songIds.map { sid ->
            PlaylistSongEntity(playlistId = playlistId, songId = sid, position = pos++)
        }
        playlistDao.addSongsToPlaylist(items)
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeFromPlaylist(playlistId, songId)
    }

    // ==================== Assets 导入(首次启动) ====================

    /**
     * 扫描 assets/music/ 并导入到数据库。
     * 首次启动时调用,已存在的文件会跳过(按 file_path 去重)。
     * @return 导入的歌曲数量
     */
    suspend fun importAssets(): Int = withContext(Dispatchers.IO) {
        val songs = assetScanner.scanAssets()
        var imported = 0
        for (song in songs) {
            // 去重:按 file_path 检查
            if (songDao.countByPath(song.filePath) == 0) {
                songDao.insert(song)
                imported++
            }
        }
        imported
    }

    // ==================== 缓存/工具 ====================

    /** 清除封面缓存等临时文件(不删除用户音乐) */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        File(context.cacheDir, "covers").deleteRecursively()
        File(context.cacheDir, "bili_downloads").deleteRecursively()
    }

    /** 计算缓存大小 */
    suspend fun calculateCacheSize(): Long = withContext(Dispatchers.IO) {
        fun dirSize(dir: File): Long =
            if (!dir.exists()) 0L
            else dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        dirSize(File(context.cacheDir, "covers")) +
            dirSize(File(context.cacheDir, "bili_downloads"))
    }

    private fun queryFileName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        return cursor.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) it.getString(nameIndex) else null
        }
    }

    private fun ensureUniqueFile(file: File): File {
        if (!file.exists()) return file
        val base = file.nameWithoutExtension
        val ext = file.extension
        var counter = 1
        var candidate = File(file.parentFile, "$base($counter).$ext")
        while (candidate.exists()) {
            counter++
            candidate = File(file.parentFile, "$base($counter).$ext")
        }
        return candidate
    }

    /** Entity → Domain Model */
    private fun toDomain(e: SongEntity): Song = Song(
        id = e.id,
        title = e.title,
        artist = e.artist,
        album = e.album,
        year = e.year,
        genre = e.genre,
        durationMs = e.durationMs,
        filePath = e.filePath,
        folderPath = e.folderPath,
        format = e.format,
        sampleRate = e.sampleRate,
        bitDepth = e.bitDepth,
        bitRate = e.bitRate,
        channels = e.channels,
        fileSize = e.fileSize,
        coverUri = e.coverUri,
        addedAt = e.addedAt,
        lastPlayedAt = e.lastPlayedAt,
        playCount = e.playCount,
        isFavorite = e.isFavorite,
        resumePosition = e.resumePosition
    )

    /** 获取歌曲的完整文件路径(播放时用)。支持 EXTERNAL: 前缀(公共目录绝对路径) */
    fun getSongFile(song: Song): File =
        if (song.filePath.startsWith("EXTERNAL:")) {
            File(song.filePath.removePrefix("EXTERNAL:"))
        } else {
            File(musicDir, song.filePath)
        }
}
