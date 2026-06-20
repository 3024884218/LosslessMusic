package com.lossless.music.di

import android.content.Context
import androidx.room.Room
import com.lossless.music.data.local.AppDatabase
import com.lossless.music.data.local.dao.HistoryDao
import com.lossless.music.data.local.dao.PlaylistDao
import com.lossless.music.data.local.dao.SongDao
import com.lossless.music.data.repository.MusicRepository
import com.lossless.music.data.scanner.AssetScanner
import com.lossless.music.data.scanner.ExternalScanner
import com.lossless.music.data.scanner.MetadataReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "lossless_music.db"
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSongDao(db: AppDatabase): SongDao = db.songDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    @Singleton
    fun provideMetadataReader(@ApplicationContext context: Context): MetadataReader =
        MetadataReader(context)

    @Provides
    @Singleton
    fun provideAssetScanner(@ApplicationContext context: Context): AssetScanner =
        AssetScanner(context)

    @Provides
    @Singleton
    fun provideExternalScanner(
        @ApplicationContext context: Context,
        metadataReader: MetadataReader
    ): ExternalScanner = ExternalScanner(context, metadataReader)

    @Provides
    @Singleton
    fun provideBiliClient(): com.lossless.music.download.BiliClient =
        com.lossless.music.download.BiliClient()

    @Provides
    @Singleton
    fun providePublicMusicExporter(@ApplicationContext context: Context): com.lossless.music.download.PublicMusicExporter =
        com.lossless.music.download.PublicMusicExporter(context)

    @Provides
    @Singleton
    fun provideOnlineLyricsFetcher(): com.lossless.music.ui.lyrics.OnlineLyricsFetcher =
        com.lossless.music.ui.lyrics.OnlineLyricsFetcher()

    @Provides
    @Singleton
    fun provideLoudnessNormalizer(@ApplicationContext context: Context): com.lossless.music.audio.LoudnessNormalizer =
        com.lossless.music.audio.LoudnessNormalizer(context)

    @Provides
    @Singleton
    fun provideSyncManager(
        @ApplicationContext context: Context,
        songDao: SongDao,
        settingsRepository: com.lossless.music.ui.settings.SettingsRepository
    ): com.lossless.music.sync.SyncManager =
        com.lossless.music.sync.SyncManager(context, songDao, settingsRepository)

    @Provides
    @Singleton
    fun provideMusicRepository(
        songDao: SongDao,
        playlistDao: PlaylistDao,
        historyDao: HistoryDao,
        metadataReader: MetadataReader,
        assetScanner: AssetScanner,
        externalScanner: ExternalScanner,
        @ApplicationContext context: Context
    ): MusicRepository = MusicRepository(songDao, playlistDao, historyDao, metadataReader, assetScanner, externalScanner, context)
}
