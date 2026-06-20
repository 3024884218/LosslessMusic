package com.lossless.music.ui.navigation

/**
 * 顶层导航目标(底部导航栏)。
 */
sealed class TopDest(val route: String, val label: String, val icon: String) {
    object All      : TopDest("all",      "全部",   "music_note")
    object Folders  : TopDest("folders",  "文件夹", "folder")
    object Artists  : TopDest("artists",  "艺术家", "person")
    object Playlists: TopDest("playlists","歌单",   "queue_music")
    object Favorites: TopDest("favorites","收藏",   "favorite")
}

/**
 * 二级页面路由。
 */
object Routes {
    const val PLAYER     = "player"          // 全屏播放页
    const val QUEUE      = "queue"           // 播放队列
    const val LYRICS     = "lyrics"          // 歌词
    const val EQUALIZER  = "equalizer"       // 均衡器
    const val DOWNLOAD   = "download"        // B站下载
    const val SETTINGS   = "settings"        // 设置
    const val BROWSER    = "browser"         // 文件浏览器
    const val SONG_DETAIL = "song_detail/{songId}"  // 歌曲详情页
    const val SONGS_IN_FOLDER  = "folder_songs/{folder}"
    const val SONGS_IN_ARTIST  = "artist_songs/{artist}"
    const val SONGS_IN_PLAYLIST = "playlist_songs/{playlistId}"
}
