package com.lossless.music.ui.songdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lossless.music.data.repository.MusicRepository
import com.lossless.music.domain.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongDetailViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {
    private val _song = MutableStateFlow<Song?>(null)
    val song: StateFlow<Song?> = _song.asStateFlow()

    fun load(songId: Long) {
        viewModelScope.launch {
            _song.value = repository.getSong(songId)
        }
    }
}
