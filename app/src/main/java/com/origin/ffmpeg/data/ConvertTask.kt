package com.origin.ffmpeg.data

import java.io.Serializable
import java.util.UUID

enum class TaskStatus {
    PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
}

enum class OutputFormat(val extension: String, val label: String, val isAudioOnly: Boolean = false) {
    MP4("mp4", "MP4"),
    MKV("mkv", "MKV"),
    AVI("avi", "AVI"),
    WEBM("webm", "WebM"),
    MOV("mov", "MOV"),
    MP3("mp3", "MP3", true),
    AAC("aac", "AAC", true),
    FLAC("flac", "FLAC", true),
    WAV("wav", "WAV", true),
    OGG("ogg", "OGG", true),
    GIF("gif", "GIF"),
    PNG_SEQ("png", "PNG序列");
}

enum class VideoCodec(val codec: String, val label: String) {
    DEFAULT("", "默认"),
    H264("libx264", "H.264"),
    H265("libx265", "H.265/HEVC"),
    VP8("libvpx", "VP8"),
    VP9("libvpx-vp9", "VP9"),
    AV1("libaom-av1", "AV1"),
    COPY("copy", "复制(不转码)");
}

enum class AudioCodec(val codec: String, val label: String) {
    DEFAULT("", "默认"),
    AAC("aac", "AAC"),
    MP3("libmp3lame", "MP3"),
    OPUS("libopus", "Opus"),
    VORBIS("libvorbis", "Vorbis"),
    FLAC("flac", "FLAC"),
    PCM("pcm_s16le", "PCM"),
    COPY("copy", "复制(不转码)");
}

enum class Resolution(val label: String, val width: Int, val height: Int) {
    ORIGINAL("原始", 0, 0),
    P1080("1080p", 1920, 1080),
    P720("720p", 1280, 720),
    P480("480p", 854, 480),
    P360("360p", 640, 360),
    P240("240p", 426, 240);
}

enum class Bitrate(val label: String, val videoBitrate: String, val audioBitrate: String) {
    ORIGINAL("原始", "", ""),
    HIGH("高", "5M", "192k"),
    MEDIUM("中", "2M", "128k"),
    LOW("低", "800k", "96k"),
    VERY_LOW("极低", "400k", "64k");
}

data class ConvertTask(
    val id: String = UUID.randomUUID().toString(),
    val inputFile: String,
    val outputFile: String,
    val outputFormat: OutputFormat,
    val videoCodec: VideoCodec = VideoCodec.DEFAULT,
    val audioCodec: AudioCodec = AudioCodec.DEFAULT,
    val resolution: Resolution = Resolution.ORIGINAL,
    val bitrate: Bitrate = Bitrate.ORIGINAL,
    val customArgs: String = "",
    var status: TaskStatus = TaskStatus.PENDING,
    var progress: Int = 0,
    var speed: String = "",
    var duration: Long = 0,        // 总时长(秒)
    var currentTime: Long = 0,     // 当前处理位置(秒)
    var log: String = "",
    var startTime: Long = 0,
    var endTime: Long = 0,
    var errorMessage: String = ""
) : Serializable {

    fun buildFFmpegCommand(ffmpegPath: String): List<String> {
        val cmd = mutableListOf(ffmpegPath, "-y", "-i", inputFile)

        // 视频编码
        if (!outputFormat.isAudioOnly) {
            if (videoCodec == VideoCodec.COPY) {
                cmd.addAll(listOf("-c:v", "copy"))
            } else if (videoCodec != VideoCodec.DEFAULT) {
                cmd.addAll(listOf("-c:v", videoCodec.codec))
            }
        } else {
            cmd.addAll(listOf("-vn")) // 音频模式，去除视频
        }

        // 音频编码
        if (audioCodec == AudioCodec.COPY) {
            cmd.addAll(listOf("-c:a", "copy"))
        } else if (audioCodec != AudioCodec.DEFAULT) {
            cmd.addAll(listOf("-c:a", audioCodec.codec))
        }

        // 分辨率
        if (resolution != Resolution.ORIGINAL && !outputFormat.isAudioOnly) {
            cmd.addAll(listOf("-vf", "scale=${resolution.width}:${resolution.height}"))
        }

        // 码率
        if (bitrate != Bitrate.ORIGINAL) {
            if (!outputFormat.isAudioOnly && bitrate.videoBitrate.isNotEmpty()) {
                cmd.addAll(listOf("-b:v", bitrate.videoBitrate))
            }
            if (bitrate.audioBitrate.isNotEmpty()) {
                cmd.addAll(listOf("-b:a", bitrate.audioBitrate))
            }
        }

        // 自定义参数
        if (customArgs.isNotBlank()) {
            cmd.addAll(customArgs.split("\\s+".toRegex()).filter { it.isNotBlank() })
        }

        // 输出文件
        cmd.add(outputFile)

        return cmd
    }

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    fun getProgressText(): String {
        return if (duration > 0) {
            "${formatDuration(currentTime)} / ${formatDuration(duration)} ($progress%)"
        } else {
            "$progress%"
        }
    }
}
