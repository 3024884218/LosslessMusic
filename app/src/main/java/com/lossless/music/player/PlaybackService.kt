package com.lossless.music.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lossless.music.MainActivity
import com.lossless.music.audio.EqualizerManager
import com.lossless.music.domain.model.PlayMode
import com.lossless.music.domain.model.Song
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 后台播放服务。
 *
 * 基于 Media3 MediaSessionService:
 *  - 自动处理通知栏 + 锁屏控制
 *  - 后台播放(前台服务 mediaPlayback)
 *  - 音频焦点(来电自动暂停)
 *
 * 音质保证:
 *  - ExoPlayer 默认不重采样,按设备原生输出
 *  - 不设置任何降位深/压缩参数
 *  - 24bit FLAC 等可直接输出(需设备支持)
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var queueManager: QueueManager

    @Inject
    lateinit var equalizerManager: EqualizerManager

    @Inject
    lateinit var repository: com.lossless.music.data.repository.MusicRepository

    @Inject
    lateinit var sleepTimerManager: SleepTimerManager

    @Inject
    lateinit var loudnessNormalizer: com.lossless.music.audio.LoudnessNormalizer

    @Inject
    lateinit var settingsRepository: com.lossless.music.ui.settings.SettingsRepository

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    /** 当前播放歌曲ID,用于断点续播 */
    private var currentSongId: Long? = null
    /** A-B 循环点(毫秒),null 表示未设置 */
    private var loopA: Long? = null
    private var loopB: Long? = null

    override fun onCreate() {
        super.onCreate()
        PlayerController.service = this  // 供 UI 层调用(简化方案)
        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)  // 耳机拔出暂停
            .build()

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        // 播完一首:清除断点 + 下一首
                        // (播放历史在 loadAndPlay 开始播放时已记录,不重复)
                        currentSongId?.let { sid ->
                            kotlinx.coroutines.MainScope().launch {
                                repository.clearResumePosition(sid)
                            }
                        }
                        playNext()
                    }
                    // STATE_READY 不在此记录播放历史,
                    // 因为 seekTo 也会触发 STATE_READY,会导致拖进度条误记。
                    // 播放历史改在 loadAndPlay() 里记录(每次切歌记录一次)。
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackService", "播放错误: ${error.errorCodeName} - ${error.message}")
                playNext()
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                equalizerManager.attach(audioSessionId)
            }

            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int
            ) {
                // A-B 循环:到达 B 点跳回 A
                val a = loopA; val b = loopB
                if (a != null && b != null) {
                    val cur = exo.currentPosition
                    if (cur >= b) exo.seekTo(a)
                }
            }
        })

        player = exo

        // 点击通知栏打开 App
        val sessionIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, sessionIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player ?: return super.onTaskRemoved(rootIntent)
        if (!p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        PlayerController.service = null
        equalizerManager.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    // ==================== 播放控制(供 ViewModel 调用) ====================

    /** 播放单首歌曲(替换队列) */
    fun playSong(song: Song) {
        queueManager.setQueue(listOf(song))
        loadAndPlay(song)
    }

    /** 播放一个列表,从指定位置开始 */
    fun playList(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        queueManager.setQueue(songs, startIndex)
        loadAndPlay(songs[startIndex])
    }

    /** 添加到队列末尾 */
    fun addToQueue(song: Song) {
        queueManager.addToQueue(song)
    }

    /** 插队:下一首播放 */
    fun playNext(song: Song) {
        queueManager.playNext(song)
    }

    /** 播放/暂停 */
    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            // 暂停前保存断点
            saveCurrentPosition()
            p.pause()
        } else {
            p.play()
        }
    }

    fun play() { player?.play() }
    fun pause() {
        saveCurrentPosition()
        player?.pause()
    }

    /** 保存当前播放位置到数据库(断点续播) */
    private fun saveCurrentPosition() {
        val sid = currentSongId ?: return
        val pos = player?.currentPosition ?: return
        if (pos > 1000) {  // 超过1秒才保存
            kotlinx.coroutines.MainScope().launch {
                repository.saveResumePosition(sid, pos)
            }
        }
    }

    /** 下一首 */
    fun skipToNext() = playNext()

    /** 上一首 */
    fun skipToPrevious() {
        queueManager.previous()?.let { loadAndPlay(it) }
    }

    /** 跳转进度 */
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    /** 切换播放模式 */
    fun cyclePlayMode(): PlayMode = queueManager.cyclePlayMode()

    // ==================== 内部 ====================

    private fun playNext() {
        // 睡眠定时器:播完当前再停
        if (sleepTimerManager.shouldStopAfterCurrent()) {
            player?.pause()
            return
        }
        // 单曲循环:不重新 prepare,直接 seekTo(0) 更高效
        val mode = queueManager.playMode.value
        val current = queueManager.current.value
        if (mode == PlayMode.REPEAT_ONE && current != null && player != null) {
            player!!.seekTo(0)
            player!!.play()
            return
        }
        queueManager.next()?.let { loadAndPlay(it) }
    }

    private fun loadAndPlay(song: Song) {
        val p = player ?: return

        // 保存上一首的断点
        saveCurrentPosition()

        currentSongId = song.id

        val uri = when {
            song.filePath.startsWith("EXTERNAL:") -> {
                val absolutePath = song.filePath.removePrefix("EXTERNAL:")
                "file://$absolutePath"
            }
            else -> try {
                assets.openFd("music/${song.filePath}").close()
                "file:///android_asset/music/${song.filePath}"
            } catch (e: Exception) {
                val file = java.io.File(filesDir, "music/${song.filePath}")
                "file://${file.absolutePath}"
            }
        }

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(song.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .apply {
                        // 封面 URI
                        song.coverUri?.let { path ->
                            if (path.startsWith("http")) setArtworkUri(Uri.parse(path))
                            else setArtworkUri(Uri.fromFile(java.io.File(path)))
                        }
                    }
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
        p.setMediaItem(mediaItem)
        p.prepare()

        // 断点续播:有保存位置则跳过去
        if (song.resumePosition > 1000 && song.resumePosition < song.durationMs) {
            p.seekTo(song.resumePosition)
        }

        // 响度归一化(若启用)
        if (settingsRepository.loudnessNormalizationEnabled) {
            kotlinx.coroutines.MainScope().launch {
                val vol = loudnessNormalizer.getNormalizedVolume(song)
                p.volume = vol
            }
        } else {
            p.volume = 1f
        }

        p.play()

        // 记录播放历史(开始播放即记一次;拖进度条走 seekTo 不会触发此方法)
        kotlinx.coroutines.MainScope().launch {
            repository.recordPlay(song.id)
        }
    }

    // ==================== A-B 循环 ====================

    /** 设置 A 点(当前播放位置) */
    fun setLoopA() {
        loopA = player?.currentPosition
    }

    /** 设置 B 点(当前播放位置) */
    fun setLoopB() {
        loopB = player?.currentPosition
    }

    /** 清除 A-B 循环 */
    fun clearLoop() {
        loopA = null
        loopB = null
    }

    /** 获取 A-B 点状态 */
    fun getLoopState(): Pair<Long?, Long?> = loopA to loopB
}
