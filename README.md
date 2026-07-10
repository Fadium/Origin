# Origin - Android FFmpeg Media Converter

一款成熟的 Android 媒体格式转换工具，内嵌 FFmpeg 二进制文件，支持视频/音频格式转换、分辨率调整、码率控制等功能。

## 功能特性

- **内嵌 FFmpeg**: 无需 Root，应用自带 FFmpeg 二进制文件
- **多格式支持**: MP4, MKV, AVI, WebM, MOV, MP3, AAC, FLAC, WAV, OGG, GIF 等
- **视频编码**: H.264, H.265/HEVC, VP8, VP9, AV1
- **音频编码**: AAC, MP3, Opus, Vorbis, FLAC, PCM
- **分辨率调整**: 原始, 1080p, 720p, 480p, 360p, 240p
- **码率控制**: 高/中/低/极低 多档可选
- **自定义参数**: 支持传入任意 FFmpeg 命令行参数
- **实时进度**: 显示处理进度、当前时间、处理速度
- **任务管理**: 历史任务列表，支持取消/删除/查看详情
- **前台服务**: 后台处理不中断
- **Material Design 3**: 现代化 UI 设计

## 项目结构

```
Origin/
├── .github/workflows/
│   ├── build-debug.yaml          # Debug 构建流水线
│   └── build-release.yaml        # Release 发布流水线
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       ├── java/com/origin/ffmpeg/
│       │   ├── OriginApplication.kt
│       │   ├── data/
│       │   │   ├── ConvertTask.kt      # 转换任务数据模型
│       │   │   ├── TaskStore.kt         # 任务持久化存储
│       │   │   └── AppPreferences.kt    # 应用偏好设置
│       │   ├── ffmpeg/
│       │   │   └── FFmpegWrapper.kt     # FFmpeg 执行封装
│       │   ├── service/
│       │   │   └── FFmpegService.kt     # 前台处理服务
│       │   └── ui/
│       │       ├── MainActivity.kt      # 主界面
│       │       ├── HomeFragment.kt      # 首页(转换配置)
│       │       ├── TasksFragment.kt     # 任务列表
│       │       ├── SettingsFragment.kt  # 设置页
│       │       └── TaskDetailActivity.kt # 任务详情
│       ├── jniLibs/                     # FFmpeg 二进制文件
│       │   ├── arm64-v8a/
│       │   ├── armeabi-v7a/
│       │   └── x86_64/
│       └── res/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/
```

## 构建说明

### 前置要求

- JDK 17
- Android SDK 35
- Gradle 8.9

### FFmpeg 二进制文件准备

本项目需要预编译的 Android FFmpeg 二进制文件。你需要将 FFmpeg 可执行文件放入 `app/src/main/jniLibs/` 目录。

#### 方式一: 使用预编译文件

1. 从 [FFmpeg-Builds](https://github.com/BtbN/FFmpeg-Builds/releases) 下载适用于 Android 的 FFmpeg
2. 或者使用 [mobile-ffmpeg](https://github.com/tanersener/mobile-ffmpeg) / [ffmpeg-kit](https://github.com/arthenica/ffmpeg-kit)
3. 将 `ffmpeg` 重命名为 `ffmpeg.so`，`ffprobe` 重命名为 `ffprobe.so`
4. 放入对应 ABI 目录:
   ```
   app/src/main/jniLibs/arm64-v8a/ffmpeg.so
   app/src/main/jniLibs/arm64-v8a/ffprobe.so
   app/src/main/jniLibs/armeabi-v7a/ffmpeg.so
   app/src/main/jniLibs/armeabi-v7a/ffprobe.so
   app/src/main/jniLibs/x86_64/ffmpeg.so
   app/src/main/jniLibs/x86_64/ffprobe.so
   ```

#### 方式二: 自行编译 FFmpeg for Android

```bash
# 使用 NDK 交叉编译 FFmpeg
# 以 arm64-v8a 为例:

export NDK=/path/to/android-ndk
export HOST_TAG=darwin-x86_64  # macOS
export TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/$HOST_TAG
export API=24

./configure \
    --prefix=$HOME/ffmpeg_build/arm64-v8a \
    --enable-cross-compile \
    --cross-prefix=$TOOLCHAIN/bin/aarch64-linux-android${API}- \
    --target-os=android \
    --arch=aarch64 \
    --cc=$TOOLCHAIN/bin/aarch64-linux-android${API}-clang \
    --cxx=$TOOLCHAIN/bin/aarch64-linux-android${API}-clang++ \
    --enable-shared \
    --disable-static \
    --disable-doc \
    --disable-ffmpeg \
    --disable-ffplay \
    --disable-ffprobe \
    --disable-doc \
    --disable-symver \
    --enable-gpl \
    --enable-version3

make -j$(nproc)
make install

# 编译产物中的 bin/ffmpeg 即为可执行文件
# 重命名为 ffmpeg.so 放入 jniLibs/arm64-v8a/
```

### 本地构建

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease
```

## GitHub Actions CI/CD

### Debug 构建 (自动)

每次推送到 `main` 分支时自动触发 Debug 构建，产物以 Artifact 形式保存。

### Release 发布 (手动)

1. 进入 GitHub Actions 页面
2. 选择 "Build Release" 工作流
3. 点击 "Run workflow"
4. 输入 Release Tag (如 `v1.0.0`)
5. 自动构建并发布到 GitHub Releases

### 签名配置

在 GitHub 仓库的 Settings > Secrets 中添加:

- `SIGNING_STORE_PASSWORD`: keystore 密码
- `SIGNING_KEY_ALIAS`: key 别名
- `SIGNING_KEY_PASSWORD`: key 密码

## 权限说明

| 权限 | 用途 |
|------|------|
| `READ_EXTERNAL_STORAGE` | 读取媒体文件 (Android 12-) |
| `READ_MEDIA_VIDEO/AUDIO/IMAGES` | 读取媒体文件 (Android 13+) |
| `WRITE_EXTERNAL_STORAGE` | 保存转换结果 (Android 10-) |
| `MANAGE_EXTERNAL_STORAGE` | 保存转换结果 (Android 11+) |
| `INTERNET` | 网络访问 |
| `FOREGROUND_SERVICE` | 后台处理 |
| `POST_NOTIFICATIONS` | 进度通知 (Android 13+) |

## 许可证

本项目采用 MIT 许可证。FFmpeg 本身采用 LGPL/GPL 许可证。

## 致谢

- [FFmpeg](https://ffmpeg.org/) - 强大的多媒体处理框架
- [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) - 项目结构参考
- [BtbN/FFmpeg-Builds](https://github.com/BtbN/FFmpeg-Builds) - 预编译 FFmpeg
