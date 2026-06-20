package com.lossless.music.player

import com.lossless.music.domain.model.PlayMode
import com.lossless.music.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放队列管理器。
 *
 * 维护两类条目:
 *  - 正常队列(按顺序播放)
 *  - Next 队列(插队的,优先于正常队列,播完一首移除一首)
 *
 * 取下一首时:优先取 Next 队列,空了再取正常队列。
 * 这就实现了"添加下一首"的插队语义。
 *
 * 当前歌曲弹出后,根据 PlayMode 决定是否重新入列(单曲循环)。
 */
@Singleton
class QueueManager @Inject constructor() {

    /** 正常播放队列 */
    private val _mainQueue = MutableStateFlow<List<Song>>(emptyList())
    val mainQueue: StateFlow<List<Song>> = _mainQueue.asStateFlow()

    /** 插队队列(Play Next) */
    private val _nextQueue = MutableStateFlow<List<Song>>(emptyList())
    val nextQueue: StateFlow<List<Song>> = _nextQueue.asStateFlow()

    /** 当前播放歌曲 */
    private val _current = MutableStateFlow<Song?>(null)
    val current: StateFlow<Song?> = _current.asStateFlow()

    /** 当前在主队列中的索引 */
    private var currentIndex = -1

    /** 播放模式 */
    private val _playMode = MutableStateFlow(PlayMode.SEQUENCE)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    /** 用于随机播放的乱序索引 */
    private var shuffleOrder: List<Int> = emptyList()
    private var shufflePointer = 0

    // ==================== 队列操作 ====================

    /** 设置主队列并从第一首开始 */
    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        _mainQueue.value = songs
        currentIndex = startIndex.coerceIn(0, songs.lastIndex.coerceAtLeast(0))
        _nextQueue.value = emptyList()
        rebuildShuffleOrder()
        _current.value = songs.getOrNull(currentIndex)
    }

    /** 添加到主队列末尾(Add to Queue) */
    fun addToQueue(song: Song) {
        _mainQueue.value = _mainQueue.value + song
        rebuildShuffleOrder()
    }

    /** 批量添加到主队列末尾 */
    fun addAllToQueue(songs: List<Song>) {
        _mainQueue.value = _mainQueue.value + songs
        rebuildShuffleOrder()
    }

    /** 替换主队列(用于手动排序) */
    fun replaceMainQueue(songs: List<Song>) {
        _mainQueue.value = songs
        rebuildShuffleOrder()
    }

    /** 插队:添加到 Next 队列末尾(Play Next) */
    fun playNext(song: Song) {
        _nextQueue.value = _nextQueue.value + song
    }

    /** 插队:添加到 Next 队列开头(立即下一首) */
    fun playImmediatelyNext(song: Song) {
        _nextQueue.value = listOf(song) + _nextQueue.value
    }

    /** 从队列移除指定歌曲 */
    fun remove(song: Song) {
        _mainQueue.value = _mainQueue.value.filterNot { it.id == song.id }
        _nextQueue.value = _nextQueue.value.filterNot { it.id == song.id }
        rebuildShuffleOrder()
    }

    /** 清空队列(保留当前) */
    fun clearQueue() {
        _mainQueue.value = emptyList()
        _nextQueue.value = emptyList()
        currentIndex = -1
        rebuildShuffleOrder()
    }

    /** 清空 Next 队列 */
    fun clearNextQueue() {
        _nextQueue.value = emptyList()
    }

    // ==================== 取歌 ====================

    /**
     * 跳到下一首并返回。
     * 优先从 Next 队列取,空了按 PlayMode 从主队列取。
     */
    fun next(): Song? {
        // 单曲循环:返回当前
        if (_playMode.value == PlayMode.REPEAT_ONE && _current.value != null) {
            return _current.value
        }
        // Next 队列优先
        if (_nextQueue.value.isNotEmpty()) {
            val nextSong = _nextQueue.value.first()
            _nextQueue.value = _nextQueue.value.drop(1)
            _current.value = nextSong
            return nextSong
        }
        // 主队列
        val main = _mainQueue.value
        if (main.isEmpty()) {
            _current.value = null
            return null
        }
        val nextIndex = when (_playMode.value) {
            PlayMode.SHUFFLE -> {
                if (shuffleOrder.isEmpty()) rebuildShuffleOrder()
                // 推进指针
                shufflePointer++
                if (shufflePointer >= shuffleOrder.size) {
                    // 一轮播完,重建乱序(新 anchor = 刚播完的 currentIndex)
                    rebuildShuffleOrder()
                    // 重建后 shuffleOrder[0] = anchor(刚播完),需跳过它
                    shufflePointer = if (shuffleOrder.size > 1) 1 else 0
                }
                shuffleOrder.getOrElse(shufflePointer) { 0 }
            }
            PlayMode.REPEAT_ALL -> (currentIndex + 1) % main.size
            PlayMode.SEQUENCE -> {
                if (currentIndex + 1 < main.size) currentIndex + 1 else -1.also {
                    _current.value = null
                    return null
                }
            }
            PlayMode.REPEAT_ONE -> (currentIndex + 1) % main.size
        }
        if (nextIndex < 0) return null
        currentIndex = nextIndex
        _current.value = main[nextIndex]
        return _current.value
    }

    /** 上一首 */
    fun previous(): Song? {
        val main = _mainQueue.value
        if (main.isEmpty()) return _current.value
        currentIndex = when (_playMode.value) {
            PlayMode.SHUFFLE -> {
                shufflePointer = (shufflePointer - 1 + shuffleOrder.size) % shuffleOrder.size
                shuffleOrder.getOrElse(shufflePointer) { 0 }
            }
            else -> (currentIndex - 1 + main.size) % main.size
        }
        _current.value = main[currentIndex]
        return _current.value
    }

    /** 跳到主队列指定位置 */
    fun jumpTo(index: Int): Song? {
        val main = _mainQueue.value
        if (index !in main.indices) return null
        currentIndex = index
        _current.value = main[index]
        return _current.value
    }

    // ==================== 播放模式 ====================

    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode
        if (mode == PlayMode.SHUFFLE) rebuildShuffleOrder()
    }

    /** 循环切换模式 */
    fun cyclePlayMode(): PlayMode {
        val next = when (_playMode.value) {
            PlayMode.SEQUENCE -> PlayMode.REPEAT_ALL
            PlayMode.REPEAT_ALL -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.SEQUENCE
        }
        setPlayMode(next)
        return next
    }

    // ==================== 内部 ====================

    /**
     * 重建乱序序列。
     *
     * 关键:把当前播放歌曲(anchor)放在 shuffleOrder[0],
     * 这样首次 next() 取 shuffleOrder[1] 时不会跳过任何一首。
     * 其余位置随机打乱。
     */
    private fun rebuildShuffleOrder(anchor: Int = currentIndex) {
        val size = _mainQueue.value.size
        shuffleOrder = if (size > 0) {
            val rest = (0 until size).filter { it != anchor }.shuffled()
            if (anchor in 0 until size) listOf(anchor) + rest else rest
        } else emptyList()
        shufflePointer = 0
    }
}
