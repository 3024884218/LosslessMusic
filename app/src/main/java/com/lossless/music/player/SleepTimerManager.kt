package com.lossless.music.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 睡眠定时器(增强版)。
 *
 * 模式:
 *  1. 定时停止(15/30/45/60/90 分钟)
 *  2. 播完当前歌曲再停
 *  3. 渐弱退出:最后 30 秒音量线性降到 0,避免突然静音
 */
@Singleton
class SleepTimerManager @Inject constructor(
    private val playerController: PlayerController
) {
    private val handler = Handler(Looper.getMainLooper())
    private var endTimeMs = AtomicLong(0)
    private var onTickCallback: ((Long) -> Unit)? = null
    private var onFinishCallback: (() -> Unit)? = null

    /** 是否"播完当前再停"模式 */
    private var finishAfterCurrent = false

    /** 是否启用渐弱 */
    private var fadeOutEnabled = false

    /** 渐弱开始时间(结束前 30 秒) */
    private var fadeStartMs = 0L

    /** 原始音量(渐弱恢复用) */
    private var originalVolume = 1f

    private val tickRunnable = object : Runnable {
        override fun run() {
            val remaining = getRemainingMs()
            onTickCallback?.invoke(remaining)

            // 渐弱阶段:最后 30 秒逐步降低音量
            if (fadeOutEnabled && remaining in 1..30_000) {
                val ratio = remaining / 30_000f
                val vol = (ratio * originalVolume).coerceIn(0f, 1f)
                setPlayerVolume(vol)
            }

            if (remaining <= 0) {
                finish()
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }

    var isRunning: Boolean = false
        private set

    /** 启动定时停止
     * @param minutes 分钟数
     * @param fadeOut 是否启用渐弱(最后 30 秒降音量)
     */
    fun start(minutes: Int, fadeOut: Boolean = false) {
        if (minutes <= 0) {
            cancel()
            return
        }
        cancel()
        originalVolume = getPlayerVolume()
        fadeOutEnabled = fadeOut
        endTimeMs.set(System.currentTimeMillis() + minutes * 60 * 1000L)
        isRunning = true
        handler.post(tickRunnable)
        Log.d("SleepTimer", "启动 $minutes 分钟定时,渐弱=$fadeOut")
    }

    /** 播完当前歌曲再停 */
    fun setFinishAfterCurrent(enabled: Boolean) {
        finishAfterCurrent = enabled
        if (enabled) {
            isRunning = true
            Log.d("SleepTimer", "已设置:播完当前歌曲后停止")
        }
    }

    /** 是否处于"播完当前再停"模式(供 PlaybackService 调用) */
    fun shouldStopAfterCurrent(): Boolean {
        if (finishAfterCurrent) {
            finishAfterCurrent = false
            isRunning = false
            return true
        }
        return false
    }

    /** 取消定时器 */
    fun cancel() {
        handler.removeCallbacks(tickRunnable)
        endTimeMs.set(0)
        isRunning = false
        finishAfterCurrent = false
        fadeOutEnabled = false
        // 恢复音量
        if (originalVolume > 0) setPlayerVolume(originalVolume)
        onTickCallback?.invoke(0)
    }

    fun getRemainingMs(): Long {
        val end = endTimeMs.get()
        if (end <= 0) return 0
        return (end - System.currentTimeMillis()).coerceAtLeast(0)
    }

    fun setOnTick(callback: (Long) -> Unit) {
        onTickCallback = callback
    }

    fun setOnFinish(callback: () -> Unit) {
        onFinishCallback = callback
    }

    private fun finish() {
        cancel()
        playerController.togglePlayPause()
        onFinishCallback?.invoke()
        Log.d("SleepTimer", "定时结束,已暂停")
    }

    /** 设置 ExoPlayer 音量(0-1) */
    private fun setPlayerVolume(vol: Float) {
        PlayerController.service?.let { svc ->
            try {
                val field = svc.javaClass.getDeclaredField("player")
                field.isAccessible = true
                val player = field.get(svc) as? androidx.media3.exoplayer.ExoPlayer
                player?.volume = vol
            } catch (_: Exception) {}
        }
    }

    /** 获取当前 ExoPlayer 音量 */
    private fun getPlayerVolume(): Float {
        return try {
            PlayerController.service?.let { svc ->
                val field = svc.javaClass.getDeclaredField("player")
                field.isAccessible = true
                (field.get(svc) as? androidx.media3.exoplayer.ExoPlayer)?.volume ?: 1f
            } ?: 1f
        } catch (_: Exception) { 1f }
    }
}
