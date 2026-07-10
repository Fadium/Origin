package com.origin.ffmpeg.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.origin.ffmpeg.data.TaskStore
import com.origin.ffmpeg.databinding.ActivityTaskDetailBinding
import java.io.File

class TaskDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskDetailBinding
    private lateinit var taskStore: TaskStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        taskStore = TaskStore(this)

        val taskId = intent.getStringExtra("task_id") ?: run {
            finish()
            return
        }

        loadTaskDetails(taskId)
    }

    private fun loadTaskDetails(taskId: String) {
        val tasks = taskStore.loadTasks()
        val task = tasks.find { it.id == taskId } ?: run {
            finish()
            return
        }

        supportActionBar?.title = File(task.inputFile).name

        binding.tvInputFile.text = "输入: ${task.inputFile}"
        binding.tvOutputFile.text = "输出: ${task.outputFile}"

        val cmd = task.buildFFmpegCommand("ffmpeg")
        binding.tvCommand.text = cmd.joinToString(" \\\n  ")

        binding.tvLog.text = task.log.ifBlank { "暂无日志" }

        // Auto scroll to bottom
        binding.scrollLog.post {
            binding.scrollLog.fullScroll(android.widget.ScrollView.FOCUS_DOWN)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
