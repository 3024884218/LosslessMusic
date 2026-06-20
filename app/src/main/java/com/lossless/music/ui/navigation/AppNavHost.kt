package com.lossless.music.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.lossless.music.ui.browser.BrowserScreen
import com.lossless.music.ui.equalizer.EqualizerScreen
import com.lossless.music.ui.home.HomeScreen
import com.lossless.music.ui.home.SongListRoute
import com.lossless.music.ui.player.PlayerScreen
import com.lossless.music.ui.queue.QueueScreen
import com.lossless.music.ui.download.DownloadScreen
import com.lossless.music.ui.settings.SettingsScreen

@Composable
fun AppNavHost() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = TopDest.All.route) {

        // 主界面
        composable(TopDest.All.route) {
            HomeScreen(
                onNavigateToPlayer = { nav.navigate(Routes.PLAYER) },
                onAddBiliClick = { nav.navigate(Routes.DOWNLOAD) },
                onFolderClick = { folder -> nav.navigate("folder_songs/${folder}") },
                onArtistClick = { artist -> nav.navigate("artist_songs/${artist}") },
                onPlaylistClick = { id -> nav.navigate("playlist_songs/$id") },
                onBrowserClick = { nav.navigate(Routes.BROWSER) },
                onEqualizerClick = { nav.navigate(Routes.EQUALIZER) },
                onSettingsClick = { nav.navigate(Routes.SETTINGS) }
            )
        }

        // 全屏播放页
        composable(Routes.PLAYER) {
            PlayerScreen(
                onBack = { nav.popBackStack() },
                onQueueClick = { nav.navigate(Routes.QUEUE) }
            )
        }

        composable(Routes.QUEUE) {
            QueueScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.DOWNLOAD) {
            DownloadScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenBiliLogin = { nav.navigate(Routes.DOWNLOAD) }
            )
        }

        composable(Routes.BROWSER) {
            BrowserScreen(
                onBack = { nav.popBackStack() },
                onNavigateToPlayer = { nav.navigate(Routes.PLAYER) }
            )
        }

        composable(Routes.EQUALIZER) {
            EqualizerScreen(onBack = { nav.popBackStack() })
        }

        // 歌曲详情页
        composable(
            route = Routes.SONG_DETAIL,
            arguments = listOf(navArgument("songId") { type = NavType.LongType })
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getLong("songId") ?: 0L
            com.lossless.music.ui.songdetail.SongDetailScreen(
                songId = songId,
                onBack = { nav.popBackStack() }
            )
        }

        // 文件夹下歌曲
        composable(
            route = Routes.SONGS_IN_FOLDER,
            arguments = listOf(navArgument("folder") { type = NavType.StringType })
        ) {
            SongListRoute(onBack = { nav.popBackStack() }, onNavigateToPlayer = { nav.navigate(Routes.PLAYER) })
        }

        // 艺术家下歌曲
        composable(
            route = Routes.SONGS_IN_ARTIST,
            arguments = listOf(navArgument("artist") { type = NavType.StringType })
        ) {
            SongListRoute(onBack = { nav.popBackStack() }, onNavigateToPlayer = { nav.navigate(Routes.PLAYER) })
        }

        // 歌单内歌曲
        composable(
            route = Routes.SONGS_IN_PLAYLIST,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) {
            SongListRoute(onBack = { nav.popBackStack() }, onNavigateToPlayer = { nav.navigate(Routes.PLAYER) })
        }
    }
}
