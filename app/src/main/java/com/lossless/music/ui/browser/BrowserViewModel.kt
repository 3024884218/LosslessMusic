package com.lossless.music.ui.browser

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lossless.music.data.repository.MusicRepository
import com.lossless.music.domain.model.Song
import com.lossless.music.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 文件浏览器 ViewModel。
 *
 * 浏览用户授权可访问的目录(默认从 App 私有目录 filesDir/music 开始),
 * 列出其中的音频文件和子文件夹。点击音频文件可播放,多选后可导入到音乐库。
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val _currentDir = MutableStateFlow(File(context.filesDir, "music"))
    val currentDir: StateFlow<File> = _currentDir.asStateFlow()

    private val _entries = MutableStateFlow<List<FileEntry>>(emptyList())
    val entries: StateFlow<List<FileEntry>> = _entries.asStateFlow()

    private val _selected = MutableStateFlow<Set<File>>(emptySet())
    val selected: StateFlow<Set<File>> = _selected.asStateFlow()

    private val _importedCount = MutableStateFlow(0)
    val importedCount: StateFlow<Int> = _importedCount.asStateFlow()

    init {
        refresh()
    }

    fun navigateInto(dir: File) {
        if (dir.isDirectory) {
            _currentDir.value = dir
            refresh()
        }
    }

    fun navigateUp(): Boolean {
        val parent = _currentDir.value.parentFile ?: return false
        // 只允许退到 filesDir 及以上,不能跳出私有目录
        if (parent.startsWith(context.filesDir)) {
            _currentDir.value = parent
            refresh()
            return true
        }
        return false
    }

    fun refresh() {
        viewModelScope.launch {
            val dir = _currentDir.value
            val list = dir.listFiles()?.toList() ?: emptyList()
            val sorted = list.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            _entries.value = sorted.map { FileEntry(it, it.isDirectory, it.name) }
        }
    }

    fun toggleSelection(file: File) {
        val current = _selected.value.toMutableSet()
        if (current.contains(file)) current.remove(file) else current.add(file)
        _selected.value = current
    }

    fun playFile(file: File) {
        // 创建临时 Song 对象,让 Service 播放
        val song = Song(
            id = file.hashCode().toLong(),
            title = file.nameWithoutExtension,
            artist = "本地文件",
            album = file.parentFile?.name ?: "",
            filePath = file.relativeTo(context.filesDir).path,
            folderPath = file.parentFile?.name ?: "",
            durationMs = 0,
            format = file.extension.uppercase(),
            sampleRate = 44100,
            bitDepth = 0,
            bitRate = 0,
            channels = 2,
            fileSize = file.length()
        )
        playerController.playSong(song)
    }

    fun importSelected(): Int {
        val files = _selected.value.toList()
        viewModelScope.launch {
            val uris = files.map { Uri.fromFile(it) }
            repository.importMultiple(uris, _currentDir.value.name)
            _importedCount.value = files.size
            _selected.value = emptySet()
            refresh()
        }
        return files.size
    }

    fun setRootDir(dir: File) {
        _currentDir.value = dir
        refresh()
    }

    /** 切换到外部存储 Download/Music(若权限允许) */
    fun tryExternalMusicDir(): Boolean {
        val external = Environment.getExternalStorageDirectory()
        val musicDir = File(external, "Music")
        if (musicDir.exists() && musicDir.canRead()) {
            _currentDir.value = musicDir
            refresh()
            return true
        }
        return false
    }
}

/** 文件条目 UI 数据 */
data class FileEntry(
    val file: File,
    val isDirectory: Boolean,
    val name: String
)
