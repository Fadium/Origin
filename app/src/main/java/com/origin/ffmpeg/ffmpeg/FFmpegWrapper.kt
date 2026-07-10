package com.origin.ffmpeg.ffmpeg

import android.content.Context
import com.origin.ffmpeg.data.ConvertTask
import com.origin.ffmpeg.data.TaskStatus
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

class FFmpegWrapper(private val context: Context) {

    private var process: Process? = null
    private val isCancelled = AtomicBoolean(false)

    interface Callback {
        fun onProgress(progress: Int, currentTime: Long, duration: Long, speed: String)
        fun onLog(line: String)
        fun onComplete(success: Boolean, errorMessage: String?)
    }

    /**
     * Get the path to the bundled FFmpeg binary.
     * The binary is copied from jniLibs to app's files dir (chmod +x).
     */
    fun getFFmpegPath(): String {
        val abi = getDeviceAbi()
        val outFile = File(context.filesDir, "ffmpeg")

        if (!outFile.exists()) {
            // Copy from assets or jniLibs
            val inputStream = context.assets.open("ffmpeg/$abi/ffmpeg")
            outFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            outFile.setExecutable(true, false)
        }

        return outFile.absolutePath
    }

    /**
     * Check if the bundled FFmpeg is available and working.
     */
    fun checkFFmpeg(): Pair<Boolean, String> {
        return try {
            val path = getFFmpegPath()
            val process = Runtime.getRuntime().exec(arrayOf(path, "-version"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val firstLine = reader.readLine() ?: ""
            process.waitFor()
            val success = firstLine.contains("ffmpeg")
            Pair(success, if (success) firstLine else "FFmpeg not found")
        } catch (e: Exception) {
            Pair(false, e.message ?: "Unknown error")
        }
    }

    /**
     * Execute an FFmpeg command with progress tracking.
     */
    fun execute(task: ConvertTask, callback: Callback) {
        isCancelled.set(false)

        val ffmpegPath = getFFmpegPath()
        val command = task.buildFFmpegCommand(ffmpegPath)

        callback.onLog("执行命令: ${command.joinToString(" ")}")
        callback.onLog("---")

        try {
            val pb = ProcessBuilder(command)
            pb.redirectErrorStream(true)
            process = pb.start()

            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            val durationPattern = Pattern.compile("Duration:\\s*(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})")
            val progressPattern = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})")
            val speedPattern = Pattern.compile("speed=\\s*([\\d.]+)x")

            var totalDuration: Long = 0

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (isCancelled.get()) {
                    process?.destroy()
                    callback.onComplete(false, "用户取消")
                    return
                }

                val l = line ?: continue
                callback.onLog(l)

                // Parse duration
                val dm = durationPattern.matcher(l)
                if (dm.find()) {
                    totalDuration = dm.group(1)!!.toLong() * 3600 +
                            dm.group(2)!!.toLong() * 60 +
                            dm.group(3)!!.toLong()
                    task.duration = totalDuration
                }

                // Parse progress
                val pm = progressPattern.matcher(l)
                if (pm.find() && totalDuration > 0) {
                    val currentTime = pm.group(1)!!.toLong() * 3600 +
                            pm.group(2)!!.toLong() * 60 +
                            pm.group(3)!!.toLong()
                    val progress = ((currentTime * 100) / totalDuration).toInt().coerceIn(0, 100)
                    task.currentTime = currentTime

                    val sm = speedPattern.matcher(l)
                    val speed = if (sm.find()) "${sm.group(1)}x" else ""

                    callback.onProgress(progress, currentTime, totalDuration, speed)
                }
            }

            val exitCode = process!!.waitFor()
            if (exitCode == 0) {
                callback.onProgress(100, totalDuration, totalDuration, "")
                callback.onComplete(true, null)
            } else {
                callback.onComplete(false, "FFmpeg 退出码: $exitCode")
            }

        } catch (e: Exception) {
            if (isCancelled.get()) {
                callback.onComplete(false, "用户取消")
            } else {
                callback.onComplete(false, e.message ?: "未知错误")
            }
        } finally {
            process = null
        }
    }

    /**
     * Cancel the running process.
     */
    fun cancel() {
        isCancelled.set(true)
        try {
            process?.destroy()
        } catch (_: Exception) {}
    }

    /**
     * Get FFmpeg media info for a file.
     */
    fun getMediaInfo(filePath: String): String {
        return try {
            val path = getFFmpegPath()
            val process = Runtime.getRuntime().exec(arrayOf(path, "-i", filePath))
            val reader = BufferedReader(InputStreamReader(process.errorStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.appendLine(line)
            }
            process.waitFor()
            sb.toString()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun getDeviceAbi(): String {
        val primaryAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            primaryAbi.contains("arm64") -> "arm64-v8a"
            primaryAbi.contains("armeabi") -> "armeabi-v7a"
            primaryAbi.contains("x86_64") -> "x86_64"
            primaryAbi.contains("x86") -> "x86"
            else -> "arm64-v8a"
        }
    }
}
