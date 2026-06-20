package com.lossless.music

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口。
 * 使用 @HiltAndroidApp 触发 Hilt 依赖注入代码生成。
 * 全应用仅此一处声明,所有 Activity/Service/ViewModel 通过 @Inject 获取依赖。
 */
@HiltAndroidApp
class LosslessMusicApp : Application()
