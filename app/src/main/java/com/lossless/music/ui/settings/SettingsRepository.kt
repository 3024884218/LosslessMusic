package com.lossless.music.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户设置仓库,用 SharedPreferences 持久化。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("lossless_music_settings", Context.MODE_PRIVATE)

    var hiResOutput: Boolean
        get() = prefs.getBoolean(KEY_HI_RES_OUTPUT, false)
        set(value) = prefs.edit { putBoolean(KEY_HI_RES_OUTPUT, value) }

    var biliCookie: String
        get() = prefs.getString(KEY_BILI_COOKIE, "") ?: ""
        set(value) = prefs.edit { putString(KEY_BILI_COOKIE, value) }

    var downloadOnlyWifi: Boolean
        get() = prefs.getBoolean(KEY_DOWNLOAD_ONLY_WIFI, false)
        set(value) = prefs.edit { putBoolean(KEY_DOWNLOAD_ONLY_WIFI, value) }

    var autoImportOnLaunch: Boolean
        get() = prefs.getBoolean(KEY_AUTO_IMPORT, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_IMPORT, value) }

    var eqEnabled: Boolean
        get() = prefs.getBoolean(KEY_EQ_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_EQ_ENABLED, value) }

    var eqGains: List<Float>
        get() = prefs.getString(KEY_EQ_GAINS, null)?.split(",")?.mapNotNull { it.toFloatOrNull() }
            ?: List(5) { 0f }
        set(value) = prefs.edit { putString(KEY_EQ_GAINS, value.joinToString(",")) }

    var loudnessGain: Int
        get() = prefs.getInt(KEY_LOUDNESS_GAIN, 0)
        set(value) = prefs.edit { putInt(KEY_LOUDNESS_GAIN, value) }

    /** 响度归一化(自动调整不同歌曲的音量) */
    var loudnessNormalizationEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOUDNESS_NORM, false)
        set(value) = prefs.edit { putBoolean(KEY_LOUDNESS_NORM, value) }

    /** 在线歌词自动获取 */
    var onlineLyricsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ONLINE_LYRICS, true)
        set(value) = prefs.edit { putBoolean(KEY_ONLINE_LYRICS, value) }

    /** WebDAV 同步配置 */
    var webdavUrl: String
        get() = prefs.getString(KEY_WEBDAV_URL, "") ?: ""
        set(value) = prefs.edit { putString(KEY_WEBDAV_URL, value) }

    var webdavUser: String
        get() = prefs.getString(KEY_WEBDAV_USER, "") ?: ""
        set(value) = prefs.edit { putString(KEY_WEBDAV_USER, value) }

    var webdavPass: String
        get() = prefs.getString(KEY_WEBDAV_PASS, "") ?: ""
        set(value) = prefs.edit { putString(KEY_WEBDAV_PASS, value) }

    /** 清除缓存统计等(实际清除需配合 CacheManager) */
    fun resetSettings() {
        prefs.edit { clear() }
    }

    companion object {
        private const val KEY_HI_RES_OUTPUT = "hi_res_output"
        private const val KEY_BILI_COOKIE = "bili_cookie"
        private const val KEY_DOWNLOAD_ONLY_WIFI = "download_only_wifi"
        private const val KEY_AUTO_IMPORT = "auto_import_on_launch"
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_EQ_GAINS = "eq_gains"
        private const val KEY_LOUDNESS_GAIN = "loudness_gain"
        private const val KEY_LOUDNESS_NORM = "loudness_normalization"
        private const val KEY_ONLINE_LYRICS = "online_lyrics"
        private const val KEY_WEBDAV_URL = "webdav_url"
        private const val KEY_WEBDAV_USER = "webdav_user"
        private const val KEY_WEBDAV_PASS = "webdav_pass"
    }
}
