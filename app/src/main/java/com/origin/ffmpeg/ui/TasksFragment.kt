package com.origin.ffmpeg.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.origin.ffmpeg.R
import com.origin.ffmpeg.data.ConvertTask
import com.origin.ffmpeg.data.TaskStatus
import com.origin.ffmpeg.data.TaskStore
import com.origin.ffmpeg.databinding.FragmentTasksBinding
import com.origin.ffmpeg.databinding.ItemTaskBinding
import com.origin.ffmpeg.service.FFmpegService
import java.io.File

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private lateinit var taskStore: TaskStore
    private lateinit var adapter: TaskAdapter
    private var service: FFmpegService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as FFmpegService.LocalBinder
            service = localBinder.getService()
            bound = true
            setupServiceListener()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        taskStore = TaskStore(requireContext())
        adapter = TaskAdapter()

        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTasks.adapter = adapter

        binding.fabClearAll.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("清除已完成任务")
                .setMessage("确定要清除所有已完成/失败/取消的任务吗？")
                .setPositiveButton("确定") { _, _ ->
                    taskStore.clearCompleted()
                    refreshTasks()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        refreshTasks()
    }

    override fun onStart() {
        super.onStart()
        Intent(requireContext(), FFmpegService::class.java).also { intent ->
            requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTasks()
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            service?.setTaskListener(null)
            requireContext().unbindService(connection)
            bound = false
        }
    }

    private fun setupServiceListener() {
        service?.setTaskListener(object : FFmpegService.TaskListener {
            override fun onTaskProgress(taskId: String, progress: Int, speed: String, log: String) {
                activity?.runOnUiThread {
                    adapter.updateTaskProgress(taskId, progress, speed)
                }
            }

            override fun onTaskComplete(taskId: String, success: Boolean, errorMessage: String?) {
                activity?.runOnUiThread {
                    refreshTasks()
                }
            }
        })
    }

    private fun refreshTasks() {
        val tasks = taskStore.loadTasks()
        adapter.submitList(tasks.toList())
        binding.tvEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
    }

    private inner class TaskAdapter : RecyclerView.Adapter<TaskAdapter.ViewHolder>() {
        private var tasks = mutableListOf<ConvertTask>()

        fun submitList(list: List<ConvertTask>) {
            tasks = list.toMutableList()
            notifyDataSetChanged()
        }

        fun updateTaskProgress(taskId: String, progress: Int, speed: String) {
            val index = tasks.indexOfFirst { it.id == taskId }
            if (index >= 0) {
                tasks[index].progress = progress
                tasks[index].speed = speed
                tasks[index].status = TaskStatus.RUNNING
                notifyItemChanged(index)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(tasks[position])
        }

        override fun getItemCount() = tasks.size

        inner class ViewHolder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {

            fun bind(task: ConvertTask) {
                binding.tvFileName.text = File(task.inputFile).name
                binding.tvFormatInfo.text = "${task.outputFormat.label} | ${task.videoCodec.label} | ${task.audioCodec.label}"
                binding.progressBar.progress = task.progress
                binding.tvProgress.text = task.getProgressText()
                binding.tvSpeed.text = if (task.speed.isNotEmpty()) "速度: ${task.speed}" else ""

                // Status
                binding.tvStatus.text = when (task.status) {
                    TaskStatus.PENDING -> "等待中"
                    TaskStatus.RUNNING -> "处理中"
                    TaskStatus.COMPLETED -> "已完成"
                    TaskStatus.FAILED -> "失败"
                    TaskStatus.CANCELLED -> "已取消"
                }

                val statusColor = when (task.status) {
                    TaskStatus.PENDING -> R.color.status_pending
                    TaskStatus.RUNNING -> R.color.status_running
                    TaskStatus.COMPLETED -> R.color.status_completed
                    TaskStatus.FAILED -> R.color.status_failed
                    TaskStatus.CANCELLED -> R.color.status_cancelled
                }
                binding.tvStatus.setTextColor(binding.root.context.getColor(statusColor))

                // Actions visibility
                binding.btnCancel.visibility = if (task.status == TaskStatus.RUNNING) View.VISIBLE else View.GONE
                binding.btnOpen.visibility = if (task.status == TaskStatus.COMPLETED) View.VISIBLE else View.GONE
                binding.btnDelete.visibility = if (task.status != TaskStatus.RUNNING) View.VISIBLE else View.GONE

                // Cancel button
                binding.btnCancel.setOnClickListener {
                    FFmpegService.cancelTask(requireContext(), task.id)
                    task.status = TaskStatus.CANCELLED
                    taskStore.updateTask(task)
                    notifyItemChanged(adapterPosition)
                }

                // Open button
                binding.btnOpen.setOnClickListener {
                    val file = File(task.outputFile)
                    if (file.exists()) {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                requireContext(),
                                "${requireContext().packageName}.fileprovider",
                                file
                            )
                            setDataAndType(uri, getMimeType(task.outputFile))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            // No app to handle
                        }
                    }
                }

                // Delete button
                binding.btnDelete.setOnClickListener {
                    taskStore.removeTask(task.id)
                    tasks.removeAt(adapterPosition)
                    notifyItemRemoved(adapterPosition)
                    this@TasksFragment.binding.tvEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
                }

                // Click to view details
                binding.root.setOnClickListener {
                    val intent = Intent(requireContext(), TaskDetailActivity::class.java).apply {
                        putExtra("task_id", task.id)
                    }
                    startActivity(intent)
                }
            }

            private fun getMimeType(path: String): String {
                return when {
                    path.endsWith(".mp4") -> "video/mp4"
                    path.endsWith(".mkv") -> "video/x-matroska"
                    path.endsWith(".avi") -> "video/x-msvideo"
                    path.endsWith(".webm") -> "video/webm"
                    path.endsWith(".mov") -> "video/quicktime"
                    path.endsWith(".mp3") -> "audio/mpeg"
                    path.endsWith(".aac") -> "audio/aac"
                    path.endsWith(".flac") -> "audio/flac"
                    path.endsWith(".wav") -> "audio/wav"
                    path.endsWith(".ogg") -> "audio/ogg"
                    path.endsWith(".gif") -> "image/gif"
                    else -> "*/*"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
