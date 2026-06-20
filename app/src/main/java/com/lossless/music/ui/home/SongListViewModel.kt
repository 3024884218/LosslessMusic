package com.lossless.music.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lossless.music.data.repository.MusicRepository
import com.lossless.music.domain.model.Song
import com.lossless.music.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 歌曲列表页 ViewModel。
 * 通过 SavedStateHandle 读取路由参数(folder/artist/playlistId),
 * 自动加载对应分类的歌曲。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SongListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MusicRepository,
    private val playerController: PlayerController
) : ViewModel() {

    // 路由参数
    val folder: String? = savedStateHandle["folder"]
    val artist: String? = savedStateHandle["artist"]
    val playlistId: Long? = savedStateHandle["playlistId"]

    /** 页面标题 */
    val title: String = folder ?: artist ?: "歌单"

    /** 歌曲列表(根据参数选择数据源) */
    val songs: StateFlow<List<Song>> = when {
        folder != null -> repository.observeSongsByFolder(folder)
        artist != null -> repository.observeSongsByArtist(artist)
        playlistId != null -> repository.observeSongsInPlaylist(playlistId)
        else -> flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        playerController.connect()
    }

    fun playSong(song: Song) {
        // 播放整个列表从当前歌曲开始
        val list = songs.value
        val idx = list.indexOfFirst { it.id == song.id }
        if (idx >= 0) playerController.playList(list, idx)
        else playerController.playSong(song)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch { repository.toggleFavorite(song.id, !song.isFavorite) }
    }

    fun renameSong(song: Song, newTitle: String) {
        viewModelScope.launch { repository.renameSong(song.id, newTitle) }
    }
}
