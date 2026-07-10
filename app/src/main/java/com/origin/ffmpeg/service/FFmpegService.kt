package com.origin.ffmpeg.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.origin.ffmpeg.OriginApplication
import com.origin.ffmpeg.R
import com.origin.ffmpeg.data.ConvertTask
import com.origin.ffmpeg.data.TaskStatus
import com.origin.ffmpeg.data.TaskStore
import com.origin.ffmpeg.ffmpeg.FFmpegWrapper
import com.origin.ffmpeg.ui.MainActivity
import java.io.File
import java.util.concurrent.Executors

class FFmpegService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): FFmpegService = this@FFmpegService
    }

    companion object {
        const val ACTION_START = "com.origin.ffmpeg.action.START"
        const val ACTION_CANCEL = "com.origin.ffmpeg.action.CANCEL"
        const val EXTRA_TASK_ID = "task_id"

        fun startTask(context: Context, taskId: String) {
            val intent = Intent(context, FFmpegService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TASK_ID, taskId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelTask(context: Context, taskId: String) {
            val intent = Intent(context, FFmpegService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }
    }

    private val binder = LocalBinder()
    private val executor = Executors.newSingleThreadExecutor()
    private val ffmpegWrapper by lazy { FFmpegWrapper(this) }
    private val taskStore by lazy { TaskStore(this) }
    private var currentTask: ConvertTask? = null
    private var listener: TaskListener? = null

    interface TaskListener {
        fun onTaskProgress(taskId: String, progress: Int, speed: String, log: String)
        fun onTaskComplete(taskId: String, success: Boolean, errorMessage: String?)
    }

    fun setTaskListener(listener: TaskListener?) {
        this.listener = listener
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                startForeground(1, createNotification("正在处理..."))
                startConvertTask(taskId)
            }
            ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                if (taskId != null && currentTask?.id == taskId) {
                    ffmpegWrapper.cancel()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startConvertTask(taskId: String) {
        val tasks = taskStore.loadTasks()
        val task = tasks.find { it.id == taskId } ?: return

        // Ensure output directory exists
        val outputFile = File(task.outputFile)
        outputFile.parentFile?.mkdirs()

        currentTask = task
        task.status = TaskStatus.RUNNING
        task.startTime = System.currentTimeMillis()
        taskStore.updateTask(task)

        updateNotification("正在处理: ${File(task.inputFile).name}")

        executor.execute {
            ffmpegWrapper.execute(task, object : FFmpegWrapper.Callback {
                override fun onProgress(progress: Int, currentTime: Long, duration: Long, speed: String) {
                    task.progress = progress
                    task.currentTime = currentTime
                    task.duration = duration
                    task.speed = speed
                    taskStore.updateTask(task)
                    listener?.onTaskProgress(task.id, progress, speed, "")
                }

                override fun onLog(line: String) {
                    task.log += line + "\n"
                }

                override fun onComplete(success: Boolean, errorMessage: String?) {
                    task.endTime = System.currentTimeMillis()
                    task.status = if (success) TaskStatus.COMPLETED else TaskStatus.FAILED
                    task.progress = if (success) 100 else task.progress
                    task.errorMessage = errorMessage ?: ""
                    taskStore.updateTask(task)
                    listener?.onTaskComplete(task.id, success, errorMessage)
                    currentTask = null

                    if (success) {
                        updateNotification("处理完成: ${File(task.inputFile).name}")
                    } else {
                        updateNotification("处理失败: ${File(task.inputFile).name}")
                    }

                    // Auto stop after a delay
                    android.os.Handler(mainLooper).postDelayed({
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }, 3000)
                }
            })
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, OriginApplication.CHANNEL_ID)
            .setContentTitle("Origin")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        ffmpegWrapper.cancel()
        executor.shutdown()
    }
}
