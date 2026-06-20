package com.lossless.music.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lossless.music.domain.model.PlayMode
import com.lossless.music.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放控制器。
 *
 * 桥接 UI 与 PlaybackService:
 *  - 通过 MediaController 与后台 Service 通信
 *  - 暴露播放状态(isPlaying, currentPosition)给 UI 观察响应式更新
 *  - 定时轮询播放进度
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueManager: QueueManager
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var isConnecting = false
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    val currentSong: StateFlow<Song?> = queueManager.current
    val playMode: StateFlow<PlayMode> = queueManager.playMode
    val mainQueue: StateFlow<List<Song>> = queueManager.mainQueue
    val nextQueue: StateFlow<List<Song>> = queueManager.nextQueue

    /** 连接到 PlaybackService(幂等,避免重复创建 MediaController) */
    fun connect() {
        if (controller != null || isConnecting) return
        isConnecting = true
        // 先尝试启动 Service(确保 service 静态引用被设置)
        try {
            context.startService(Intent(context, PlaybackService::class.java))
        } catch (_: Exception) {
        }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync().also { future ->
            future.addListener({
                controller = future.get()
                isConnecting = false
                controller?.let { c ->
                    c.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _isPlaying.value = isPlaying
                            if (isPlaying) startProgressPolling() else stopProgressPolling()
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            // 切换歌曲时立即更新总时长;Media3 此时 duration 可能还没刷新,用数据库时长兜底
                            val dur = c.duration.coerceAtLeast(0L)
                            _duration.value = if (dur > 0) dur else currentSong.value?.durationMs ?: 0L
                        }
                    })
                    // 初始时长(用数据库时长兜底,避免 UI 一开始显示 0:00)
                    _duration.value = c.duration.coerceAtLeast(0L).takeIf { it > 0 }
                        ?: currentSong.value?.durationMs ?: 0L
                }
            }, MoreExecutors.directExecutor())
        }
    }

    fun disconnect() {
        stopProgressPolling()
        controllerFuture?.let {
            if (!it.isDone) it.cancel(false)
        }
        controllerFuture = null
        controller?.release()
        controller = null
        isConnecting = false
    }

    /** 定时轮询播放进度 */
    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val c = controller ?: break
                _currentPosition.value = c.currentPosition
                _duration.value = c.duration.coerceAtLeast(0L)
                delay(500)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    // ==================== 控制方法 ====================

    fun playSong(song: Song) {
        ensureServiceConnected()
        service?.playSong(song)
    }

    fun playList(songs: List<Song>, startIndex: Int = 0) {
        ensureServiceConnected()
        service?.playList(songs, startIndex)
    }

    private fun ensureServiceConnected() {
        if (service == null) connect()
    }

    fun addToQueue(song: Song) { service?.addToQueue(song) }
    fun playNext(song: Song) { service?.playNext(song) }

    fun togglePlayPause() {
        val c = controller
        if (c != null) {
            if (c.isPlaying) c.pause() else c.play()
        } else {
            service?.togglePlayPause()
        }
    }

    fun skipToNext() {
        // service 已就绪:直接调用,走 QueueManager 维护的队列
        val svc = service
        if (svc != null) { svc.skipToNext(); return }

        // service 未就绪:启动 Service 并轮询等待(最多 3 秒)
        connect()
        scope.launch {
            var retries = 0
            while (service == null && retries < 30) {
                delay(100); retries++
            }
            service?.skipToNext()
        }
    }

    fun skipToPrevious() {
        val svc = service
        if (svc != null) { svc.skipToPrevious(); return }

        connect()
        scope.launch {
            var retries = 0
            while (service == null && retries < 30) {
                delay(100); retries++
            }
            service?.skipToPrevious()
        }
    }

    fun seekTo(ms: Long) {
        val c = controller
        if (c != null) c.seekTo(ms) else service?.seekTo(ms)
    }

    fun cyclePlayMode(): PlayMode = service?.cyclePlayMode() ?: PlayMode.SEQUENCE

    // ==================== A-B 循环 ====================

    fun setLoopA() { service?.setLoopA() }
    fun setLoopB() { service?.setLoopB() }
    fun clearLoop() { service?.clearLoop() }
    fun getLoopState(): Pair<Long?, Long?> = service?.getLoopState() ?: (null to null)

    companion object {
        var service: PlaybackService? = null
    }
}
