# LosslessMusic — 无损音乐播放器

Android 原生无损音乐播放器,基于 Kotlin + Jetpack Compose + Media3。

## 功能

- ✅ 无损音频播放(MP3/FLAC/WAV/APE/M4A 等,不重采样不压缩)
- ✅ 文件导入(系统选择器 SAF)
- ✅ 多维度分类:全部 / 文件夹 / 艺术家 / 歌单 / 收藏
- ✅ 播放队列 + 插队播放(Play Next)
- ✅ 播放模式:顺序 / 随机 / 单曲循环 / 列表循环
- ✅ 后台播放 + 通知栏 + 锁屏控制
- ✅ 特别收藏
- ⏳ 歌词同步(M5)
- ⏳ 音效均衡器(M6)
- ⏳ 睡眠定时器(M7)
- ⏳ B站链接下载音频(M8)
- ⏳ 内置文件浏览器导入(M9)

## 技术栈

| 模块 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 |
| 播放 | Media3 (ExoPlayer) |
| 数据库 | Room |
| DI | Hilt |
| 异步 | Coroutines + Flow |
| 构建 | Gradle Kotlin DSL + 版本目录 |

## 项目结构

```
app/src/main/java/com/lossless/music/
├── di/                  # Hilt 依赖注入
├── data/
│   ├── local/          # Room: Entity / DAO / Database
│   ├── repository/     # MusicRepository
│   └── scanner/        # MetadataReader (ID3 解析)
├── domain/model/       # 领域模型
├── player/             # Media3 播放服务 + 队列管理
├── ui/
│   ├── theme/          # Material 3 主题
│   ├── navigation/     # 导航
│   ├── home/           # 主界面(5个Tab)
│   ├── player/         # 全屏播放页
│   ├── queue/          # 播放队列
│   ├── download/       # B站下载
│   └── settings/       # 设置
├── LosslessMusicApp.kt # Application
└── MainActivity.kt
```

## 开发里程碑

- [x] **M1** 基础骨架:Gradle + Room + 导入 + 主界面列表
- [x] **M2** 播放核心:Media3 + 队列 + 插队 + 播放模式
- [x] **M4** 后台播放:MediaSession + 通知栏 + 锁屏
- [ ] **M3** 分类详情页(文件夹/艺术家/歌单内歌曲列表)
- [ ] **M5** 歌词同步
- [ ] **M6** 均衡器
- [ ] **M7** 睡眠定时器
- [ ] **M8** B站下载(yt-dlp + WebView 登录)
- [ ] **M9** 内置文件浏览器
- [ ] **M10** 打磨优化

## 构建

需要 Android Studio Koala(2024.1.1)+ 及 JDK 17。

```
SDK 路径: E:\SDK (见 local.properties)
```

在 Android Studio 中打开 `E:\MyAndroidProjects\LosslessMusic` 即可。
