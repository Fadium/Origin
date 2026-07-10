package com.origin.ffmpeg.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.origin.ffmpeg.BuildConfig
import com.origin.ffmpeg.R
import com.origin.ffmpeg.data.AppPreferences
import com.origin.ffmpeg.databinding.FragmentSettingsBinding
import com.origin.ffmpeg.ffmpeg.FFmpegWrapper

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences
    private lateinit var ffmpegWrapper: FFmpegWrapper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = AppPreferences(requireContext())
        ffmpegWrapper = FFmpegWrapper(requireContext())

        setupUI()
        checkFFmpeg()
    }

    private fun setupUI() {
        binding.tvOutputDir.text = prefs.outputDirectory
        binding.tvAppVersion.text = String.format(getString(R.string.settings_version), BuildConfig.VERSION_NAME)

        val themeNames = arrayOf("跟随系统", "浅色模式", "深色模式")
        binding.tvTheme.text = themeNames[prefs.themeMode]

        binding.btnCheckFFmpeg.setOnClickListener {
            checkFFmpeg()
        }

        binding.itemTheme.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择主题")
                .setSingleChoiceItems(themeNames, prefs.themeMode) { dialog, which ->
                    prefs.themeMode = which
                    binding.tvTheme.text = themeNames[which]
                    applyTheme(which)
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun checkFFmpeg() {
        binding.tvFFmpegStatus.text = "正在检测..."
        binding.tvFFmpegVersion.text = ""

        Thread {
            val (available, info) = ffmpegWrapper.checkFFmpeg()
            activity?.runOnUiThread {
                if (available) {
                    binding.tvFFmpegStatus.text = getString(R.string.settings_ffmpeg_ok)
                    binding.tvFFmpegStatus.setTextColor(requireContext().getColor(R.color.status_completed))
                    binding.tvFFmpegVersion.text = info
                    binding.tvFFmpegVersionInfo.text = info
                } else {
                    binding.tvFFmpegStatus.text = getString(R.string.settings_ffmpeg_fail)
                    binding.tvFFmpegStatus.setTextColor(requireContext().getColor(R.color.status_failed))
                    binding.tvFFmpegVersion.text = info
                    binding.tvFFmpegVersionInfo.text = "不可用"
                }
            }
        }.start()
    }

    private fun applyTheme(mode: Int) {
        when (mode) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
