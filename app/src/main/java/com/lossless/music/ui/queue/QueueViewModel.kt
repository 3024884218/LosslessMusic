package com.lossless.music.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lossless.music.domain.model.Song
import com.lossless.music.player.PlayerController
import com.lossless.music.player.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 播放队列页 ViewModel。
 * 直接观察 QueueManager 的队列,并提供移除/清空/播放下一首等操作。
 */
@HiltViewModel
class QueueViewModel @Inject constructor(
    private val queueManager: QueueManager,
    private val playerController: PlayerController
) : ViewModel() {

    val currentSong: StateFlow<Song?> = queueManager.current
    val mainQueue: StateFlow<List<Song>> = queueManager.mainQueue
    val nextQueue: StateFlow<List<Song>> = queueManager.nextQueue

    /** 从主队列移除某首歌 */
    fun removeFromQueue(song: Song) {
        queueManager.remove(song)
    }

    /** 清空下一首队列 */
    fun clearNextQueue() {
        queueManager.clearNextQueue()
    }

    /** 清空主队列(保留当前播放) */
    fun clearMainQueue() {
        queueManager.clearQueue()
        // 清空后当前播放也被清掉了,这里改为只清空主队列和Next队列,保留当前
        // 如果当前正在播放,把当前重新设为队列
        currentSong.value?.let { current ->
            queueManager.setQueue(listOf(current), 0)
        }
    }

    /** 播放某一首(替换当前队列从该位置开始) */
    fun playSong(song: Song) {
        val combined = nextQueue.value + mainQueue.value
        val index = combined.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        val list = combined.takeIf { it.isNotEmpty() } ?: listOf(song)
        playerController.playList(list, index)
    }

    /** 插到下一首播放 */
    fun playNext(song: Song) {
        playerController.playNext(song)
    }

    /** 切到下一首 */
    fun skipToNext() {
        playerController.skipToNext()
    }

    /** 切到上一首 */
    fun skipToPrevious() {
        playerController.skipToPrevious()
    }

    /** 暂停/播放切换 */
    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    /** 移动主队列中歌曲位置(从 fromIndex 到 toIndex) */
    fun moveMainQueueItem(fromIndex: Int, toIndex: Int) {
        val current = mainQueue.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            queueManager.replaceMainQueue(current)
        }
    }

    /** 把当前播放中的歌重新放回主队列首位(防止清空后无后续) */
    private fun restoreCurrentToQueue() {
        viewModelScope.launch {
            currentSong.value?.let { current ->
                if (mainQueue.value.none { it.id == current.id }) {
                    queueManager.addToQueue(current)
                }
            }
        }
    }
}
