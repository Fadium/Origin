package com.origin.ffmpeg.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.origin.ffmpeg.R
import com.origin.ffmpeg.data.*
import com.origin.ffmpeg.databinding.FragmentHomeBinding
import com.origin.ffmpeg.ffmpeg.FFmpegWrapper
import com.origin.ffmpeg.service.FFmpegService
import java.io.File

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences
    private lateinit var taskStore: TaskStore
    private lateinit var ffmpegWrapper: FFmpegWrapper
    private var selectedInputFile: String? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { handleFileSelected(it) }
    }

    private val outputDirPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            requireContext().contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val path = uriToPath(it) ?: it.path
            if (path != null) {
                prefs.outputDirectory = path
                binding.etOutputPath.setText(path)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = AppPreferences(requireContext())
        taskStore = TaskStore(requireContext())
        ffmpegWrapper = FFmpegWrapper(requireContext())

        setupSpinners()
        setupClickListeners()
        loadDefaults()
    }

    private fun setupSpinners() {
        // Format spinner
        val formats = OutputFormat.entries.map { it.label }
        val formatAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, formats)
        binding.spinnerFormat.setAdapter(formatAdapter)
        binding.spinnerFormat.setOnItemClickListener { _, _, position, _ ->
            updateCodecVisibility(OutputFormat.entries[position])
            updateCommandPreview()
        }

        // Video codec spinner
        val videoCodecs = VideoCodec.entries.map { it.label }
        val videoCodecAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, videoCodecs)
        binding.spinnerVideoCodec.setAdapter(videoCodecAdapter)
        binding.spinnerVideoCodec.setOnItemClickListener { _, _, _, _ -> updateCommandPreview() }

        // Audio codec spinner
        val audioCodecs = AudioCodec.entries.map { it.label }
        val audioCodecAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, audioCodecs)
        binding.spinnerAudioCodec.setAdapter(audioCodecAdapter)
        binding.spinnerAudioCodec.setOnItemClickListener { _, _, _, _ -> updateCommandPreview() }

        // Resolution spinner
        val resolutions = Resolution.entries.map { it.label }
        val resolutionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, resolutions)
        binding.spinnerResolution.setAdapter(resolutionAdapter)
        binding.spinnerResolution.setOnItemClickListener { _, _, _, _ -> updateCommandPreview() }

        // Bitrate spinner
        val bitrates = Bitrate.entries.map { it.label }
        val bitrateAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, bitrates)
        binding.spinnerBitrate.setAdapter(bitrateAdapter)
        binding.spinnerBitrate.setOnItemClickListener { _, _, _, _ -> updateCommandPreview() }
    }

    private fun setupClickListeners() {
        binding.btnSelectFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("*/*"))
        }

        binding.etInputFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("*/*"))
        }

        binding.etOutputPath.setOnClickListener {
            outputDirPicker.launch(null)
        }

        binding.btnStartConvert.setOnClickListener {
            startConvert()
        }
    }

    private fun loadDefaults() {
        val defaultFormat = OutputFormat.valueOf(prefs.lastUsedFormat)
        binding.spinnerFormat.setText(defaultFormat.label, false)
        binding.spinnerVideoCodec.setText(VideoCodec.DEFAULT.label, false)
        binding.spinnerAudioCodec.setText(AudioCodec.DEFAULT.label, false)
        binding.spinnerResolution.setText(Resolution.ORIGINAL.label, false)
        binding.spinnerBitrate.setText(Bitrate.ORIGINAL.label, false)
        binding.etOutputPath.setText(prefs.outputDirectory)
    }

    private fun handleFileSelected(uri: Uri) {
        val path = uriToPath(uri)
        if (path != null) {
            selectedInputFile = path
            binding.etInputFile.setText(path)
            showMediaInfo(path)
            updateCommandPreview()

            // Auto-set output directory to same folder
            val parent = File(path).parent ?: prefs.outputDirectory
            binding.etOutputPath.setText(parent)
        } else {
            Toast.makeText(requireContext(), "无法读取文件路径", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMediaInfo(filePath: String) {
        try {
            val info = ffmpegWrapper.getMediaInfo(filePath)
            if (info.isNotBlank()) {
                // Extract key info
                val lines = info.lines().filter { line ->
                    line.contains("Duration") || line.contains("Stream") || line.contains("Video") || line.contains("Audio")
                }
                if (lines.isNotEmpty()) {
                    binding.tvMediaInfo.text = lines.joinToString("\n")
                    binding.tvMediaInfo.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            binding.tvMediaInfo.visibility = View.GONE
        }
    }

    private fun updateCodecVisibility(format: OutputFormat) {
        if (format.isAudioOnly) {
            binding.spinnerVideoCodec.isEnabled = false
            binding.spinnerVideoCodec.setText(VideoCodec.DEFAULT.label, false)
            binding.spinnerResolution.isEnabled = false
            binding.spinnerResolution.setText(Resolution.ORIGINAL.label, false)
        } else {
            binding.spinnerVideoCodec.isEnabled = true
            binding.spinnerResolution.isEnabled = true
        }
    }

    private fun updateCommandPreview() {
        val task = buildTask() ?: run {
            binding.tvCommandPreview.text = "请先选择输入文件"
            return
        }
        val cmd = task.buildFFmpegCommand("ffmpeg")
        binding.tvCommandPreview.text = cmd.joinToString(" \\\n  ")
    }

    private fun buildTask(): ConvertTask? {
        val input = selectedInputFile ?: return null
        val formatLabel = binding.spinnerFormat.text.toString()
        val format = OutputFormat.entries.find { it.label == formatLabel } ?: OutputFormat.MP4

        val videoCodecLabel = binding.spinnerVideoCodec.text.toString()
        val videoCodec = VideoCodec.entries.find { it.label == videoCodecLabel } ?: VideoCodec.DEFAULT

        val audioCodecLabel = binding.spinnerAudioCodec.text.toString()
        val audioCodec = AudioCodec.entries.find { it.label == audioCodecLabel } ?: AudioCodec.DEFAULT

        val resolutionLabel = binding.spinnerResolution.text.toString()
        val resolution = Resolution.entries.find { it.label == resolutionLabel } ?: Resolution.ORIGINAL

        val bitrateLabel = binding.spinnerBitrate.text.toString()
        val bitrate = Bitrate.entries.find { it.label == bitrateLabel } ?: Bitrate.ORIGINAL

        val customArgs = binding.etCustomArgs.text?.toString() ?: ""
        val outputDir = binding.etOutputPath.text?.toString() ?: prefs.outputDirectory

        // Build output file path
        val inputFile = File(input)
        val baseName = inputFile.nameWithoutExtension
        val outputFile = File(outputDir, "${baseName}_converted.${format.extension}").absolutePath

        return ConvertTask(
            inputFile = input,
            outputFile = outputFile,
            outputFormat = format,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            resolution = resolution,
            bitrate = bitrate,
            customArgs = customArgs
        )
    }

    private fun startConvert() {
        val task = buildTask() ?: run {
            Toast.makeText(requireContext(), getString(R.string.msg_no_file_selected), Toast.LENGTH_SHORT).show()
            return
        }

        // Save task
        taskStore.addTask(task)

        // Save last used preferences
        prefs.lastUsedFormat = task.outputFormat.name
        prefs.lastUsedVideoCodec = task.videoCodec.name
        prefs.lastUsedAudioCodec = task.audioCodec.name

        // Start service
        FFmpegService.startTask(requireContext(), task.id)

        Toast.makeText(requireContext(), getString(R.string.msg_task_started), Toast.LENGTH_SHORT).show()

        // Switch to tasks tab
        (activity as? MainActivity)?.let {
            // Switch to tasks tab via ViewPager
            val viewPager = it.findViewById<androidx.viewpager2.widget.ViewPager2>(
                com.origin.ffmpeg.R.id.viewPager
            )
            viewPager.currentItem = 1
        }
    }

    private fun uriToPath(uri: Uri): String? {
        // Try direct path
        uri.path?.let { path ->
            if (path.contains("/storage/emulated") || path.contains("/sdcard")) {
                return path.replace("/document/primary:", "/storage/emulated/0/")
                    .replace("/tree/primary:", "/storage/emulated/0/")
            }
        }

        // Content resolver query
        if (DocumentsContract.isDocumentUri(requireContext(), uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            if ("com.android.providers.downloads.documents" == uri.authority) {
                if (docId.startsWith("raw:")) {
                    return docId.removePrefix("raw:")
                }
            } else if ("com.android.externalstorage.documents" == uri.authority) {
                val parts = docId.split(":")
                if (parts.size == 2) {
                    val type = parts[0]
                    val path = parts[1]
                    if ("primary".equals(type, ignoreCase = true)) {
                        return "${Environment.getExternalStorageDirectory()}/$path"
                    }
                }
            }
        }

        return null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
