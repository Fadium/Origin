package com.origin.ffmpeg.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var outputDirectory: String
        get() = prefs.getString("output_dir", getDefaultOutputDir()) ?: getDefaultOutputDir()
        set(value) = prefs.edit().putString("output_dir", value).apply()

    var ffmpegPath: String
        get() = prefs.getString("ffmpeg_path", "") ?: ""
        set(value) = prefs.edit().putString("ffmpeg_path", value).apply()

    var useBuiltInFFmpeg: Boolean
        get() = prefs.getBoolean("use_builtin_ffmpeg", true)
        set(value) = prefs.edit().putBoolean("use_builtin_ffmpeg", value).apply()

    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0) // 0=system, 1=light, 2=dark
        set(value) = prefs.edit().putInt("theme_mode", value).apply()

    var lastUsedFormat: String
        get() = prefs.getString("last_format", OutputFormat.MP4.name) ?: OutputFormat.MP4.name
        set(value) = prefs.edit().putString("last_format", value).apply()

    var lastUsedVideoCodec: String
        get() = prefs.getString("last_video_codec", VideoCodec.DEFAULT.name) ?: VideoCodec.DEFAULT.name
        set(value) = prefs.edit().putString("last_video_codec", value).apply()

    var lastUsedAudioCodec: String
        get() = prefs.getString("last_audio_codec", AudioCodec.DEFAULT.name) ?: AudioCodec.DEFAULT.name
        set(value) = prefs.edit().putString("last_audio_codec", value).apply()

    private fun getDefaultOutputDir(): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        return "${dir.absolutePath}/Origin"
    }
}
