package com.origin.ffmpeg.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TaskStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("convert_tasks", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveTasks(tasks: List<ConvertTask>) {
        val json = gson.toJson(tasks)
        prefs.edit().putString("tasks", json).apply()
    }

    fun loadTasks(): MutableList<ConvertTask> {
        val json = prefs.getString("tasks", null) ?: return mutableListOf()
        val type = object : TypeToken<List<ConvertTask>>() {}.type
        return try {
            gson.fromJson<List<ConvertTask>>(json, type).toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun addTask(task: ConvertTask) {
        val tasks = loadTasks()
        tasks.add(0, task)
        saveTasks(tasks)
    }

    fun updateTask(task: ConvertTask) {
        val tasks = loadTasks()
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            tasks[index] = task
            saveTasks(tasks)
        }
    }

    fun removeTask(taskId: String) {
        val tasks = loadTasks()
        tasks.removeAll { it.id == taskId }
        saveTasks(tasks)
    }

    fun clearCompleted() {
        val tasks = loadTasks()
        tasks.removeAll { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED || it.status == TaskStatus.CANCELLED }
        saveTasks(tasks)
    }
}
