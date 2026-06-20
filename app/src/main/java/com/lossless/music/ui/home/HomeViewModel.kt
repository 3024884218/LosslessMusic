package com.lossless.music.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lossless.music.data.local.dao.AlbumCount
import com.lossless.music.data.local.dao.ArtistCount
import com.lossless.music.data.local.dao.FolderCount
import com.lossless.music.data.local.entity.PlaylistEntity
import com.lossless.music.data.repository.MusicRepository
import com.lossless.music.domain.model.Song
import com.lossless.music.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playerController: PlayerController
) : ViewModel() {

    init {
        // 首次启动:导入 assets 内置音频 + 外部音乐目录 + 连接播放服务
        viewModelScope.launch {
            repository.importAssets()
            repository.importExternalMusicDir()
        }
        playerController.connect()
    }

    /** 播放单首歌曲 */
    fun playSong(song: Song) {
        playerController.playSong(song)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val allSongs: Flow<List<Song>> = _searchQuery
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) repository.observeAllSongs()
            else repository.search(q)
        }

    val favorites: Flow<List<Song>> = repository.observeFavorites()
    val folders: Flow<List<FolderCount>> = repository.observeFolders()
    val artists: Flow<List<ArtistCount>> = repository.observeArtists()
    val playlists: Flow<List<PlaylistEntity>> = repository.observeAllPlaylists()

    // 智能歌单
    val recentPlayed: Flow<List<Song>> = repository.observeRecentPlayed()
    val topPlayed: Flow<List<Song>> = repository.observeTopPlayed()
    val neverPlayed: Flow<List<Song>> = repository.observeNeverPlayed()
    val biliDownloads: Flow<List<Song>> = repository.observeBiliDownloads()
    val playedLast7Days: Flow<List<Song>> = repository.observePlayedLast7Days()

    fun onSearchQueryChange(q: String) { _searchQuery.value = q }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch { repository.toggleFavorite(song.id, !song.isFavorite) }
    }

    /** 重命名歌曲 */
    fun renameSong(song: Song, newTitle: String) {
        viewModelScope.launch { repository.renameSong(song.id, newTitle) }
    }

    /** 导入选中的文件,返回成功导入的数量 */
    suspend fun importUris(uris: List<Uri>): Int {
        val result = repository.importMultiple(uris, "导入")
        return result.size
    }

    /** 创建歌单 */
    suspend fun createPlaylist(name: String) {
        repository.createPlaylist(name)
    }

    /** 批量收藏 */
    fun batchFavorite(songs: List<Song>, favorite: Boolean) {
        viewModelScope.launch {
            songs.forEach { repository.toggleFavorite(it.id, favorite) }
        }
    }

    /** 批量删除 */
    fun batchDelete(songs: List<Song>) {
        viewModelScope.launch {
            songs.forEach { repository.deleteSong(it.id) }
        }
    }
}
