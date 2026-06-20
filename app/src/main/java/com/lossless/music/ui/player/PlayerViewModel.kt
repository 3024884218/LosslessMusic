package com.lossless.music.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lossless.music.data.repository.MusicRepository
import com.lossless.music.domain.model.PlayMode
import com.lossless.music.domain.model.Song
import com.lossless.music.player.PlayerController
import com.lossless.music.player.SleepTimerManager
import com.lossless.music.ui.lyrics.LrcLine
import com.lossless.music.ui.lyrics.LrcParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: PlayerController,
    private val repository: MusicRepository,
    @ApplicationContext private val context: Context,
    private val sleepTimerManager: SleepTimerManager,
    private val onlineLyricsFetcher: com.lossless.music.ui.lyrics.OnlineLyricsFetcher,
    private val settingsRepository: com.lossless.music.ui.settings.SettingsRepository
) : ViewModel() {

    val currentSong: StateFlow<Song?> = controller.currentSong
    val isPlaying: StateFlow<Boolean> = controller.isPlaying
    val playMode: StateFlow<PlayMode> = controller.playMode

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    val lyrics: StateFlow<List<LrcLine>> = _lyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    /** A-B 循环状态 */
    private val _loopA = MutableStateFlow<Long?>(null)
    val loopA: StateFlow<Long?> = _loopA.asStateFlow()
    private val _loopB = MutableStateFlow<Long?>(null)
    val loopB: StateFlow<Long?> = _loopB.asStateFlow()

    init {
        // 连接到播放服务
        controller.connect()
        // 启动进度轮询(只启动一次,避免 PlayerScreen 反复进出时重复)
        startProgressTick()
        // 监听歌曲变化,自动加载歌词
        observeCurrentSongForLyrics()
    }

    private fun observeCurrentSongForLyrics() {
        viewModelScope.launch {
            currentSong.collect { song ->
                song?.let { loadLyrics(it) } ?: run {
                    _lyrics.value = emptyList()
                    _currentLyricIndex.value = -1
                }
            }
        }
    }

    private fun loadLyrics(song: Song) {
        viewModelScope.launch {
            // 1. 先尝试本地 LRC 文件
            val localLrc = LrcParser.parseFile(context, song.filePath)
            if (localLrc.isNotEmpty()) {
                _lyrics.value = localLrc
                return@launch
            }
            // 2. 在线获取(若启用)
            if (settingsRepository.onlineLyricsEnabled) {
                val onlineLrc = onlineLyricsFetcher.fetchLyrics(song.title, song.artist, song.durationMs)
                if (!onlineLrc.isNullOrBlank()) {
                    _lyrics.value = LrcParser.parseText(onlineLrc)
                    return@launch
                }
            }
            _lyrics.value = emptyList()
        }
    }

    /** 定时刷新播放进度与歌词索引 */
    fun startProgressTick() {
        viewModelScope.launch {
            while (true) {
                val pos = controller.currentPosition.value
                val dur = controller.duration.value
                _currentPosition.value = pos
                _duration.value = if (dur > 0) dur else currentSong.value?.durationMs ?: 0L
                _currentLyricIndex.value = LrcParser.findCurrentIndex(_lyrics.value, pos)
                delay(500)
            }
        }
    }

    fun togglePlayPause() = controller.togglePlayPause()
    fun skipToNext() = controller.skipToNext()
    fun skipToPrevious() = controller.skipToPrevious()
    fun seekTo(ms: Long) = controller.seekTo(ms)
    fun cyclePlayMode() = controller.cyclePlayMode()

    fun toggleFavorite() {
        val s = currentSong.value ?: return
        viewModelScope.launch { repository.toggleFavorite(s.id, !s.isFavorite) }
    }

    /** 播放单首(由 HomeScreen 点击歌曲调用) */
    fun playSong(song: Song) {
        controller.playSong(song)
    }

    /** 播放列表(由详情页调用) */
    fun playList(songs: List<Song>, startIndex: Int = 0) {
        controller.playList(songs, startIndex)
    }

    /** 加入队列 */
    fun addToQueue(song: Song) = controller.addToQueue(song)

    /** 插队播放 */
    fun playNext(song: Song) = controller.playNext(song)

    // ==================== 睡眠定时器(增强) ====================

    fun startSleepTimer(minutes: Int, fadeOut: Boolean = false) =
        sleepTimerManager.start(minutes, fadeOut)
    fun cancelSleepTimer() = sleepTimerManager.cancel()
    fun getSleepTimerRemainingMs(): Long = sleepTimerManager.getRemainingMs()
    val isSleepTimerRunning: Boolean get() = sleepTimerManager.isRunning

    /** 播完当前再停 */
    fun setFinishAfterCurrent(enabled: Boolean) =
        sleepTimerManager.setFinishAfterCurrent(enabled)

    // ==================== A-B 循环 ====================

    fun setLoopA() {
        controller.setLoopA()
        _loopA.value = controller.currentPosition.value
    }
    fun setLoopB() {
        controller.setLoopB()
        _loopB.value = controller.currentPosition.value
    }
    fun clearLoop() {
        controller.clearLoop()
        _loopA.value = null
        _loopB.value = null
    }
}
