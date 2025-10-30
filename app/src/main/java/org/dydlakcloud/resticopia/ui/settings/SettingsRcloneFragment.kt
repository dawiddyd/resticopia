package org.dydlakcloud.resticopia.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import org.dydlakcloud.resticopia.BackupManager
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.databinding.FragmentSettingsRcloneBinding

/**
 * Rclone Configuration Settings Fragment
 * 
 * Manages Rclone configuration:
 * - View current configuration status
 * - Edit Rclone configuration file
 */
class SettingsRcloneFragment : Fragment() {

    private var _binding: FragmentSettingsRcloneBinding? = null
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    private val rcloneConfigEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("config")?.let { newConfig ->
                backupManager.configure { config ->
                    config.copy(rcloneConfig = newConfig)
                }.handle { _, throwable ->
                    if (throwable != null) {
                        throwable.printStackTrace()
                    }
                    activity?.runOnUiThread {
                        updateRcloneStatus()
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsRcloneBinding.inflate(inflater, container, false)
        _backupManager = BackupManager.instance(requireContext())

        updateRcloneStatus()
        setupConfigureButton()

        return binding.root
    }

    private fun setupConfigureButton() {
        binding.buttonRcloneConfigure.setOnClickListener {
            val intent = Intent(requireContext(), RcloneConfigEditorActivity::class.java)
            intent.putExtra("config", backupManager.config.rcloneConfig ?: "")
            rcloneConfigEditorLauncher.launch(intent)
        }
    }

    private fun updateRcloneStatus() {
        val rcloneConfig = backupManager.config.rcloneConfig
        val statusText = if (rcloneConfig.isNullOrEmpty()) {
            getString(R.string.settings_rclone_summary_not_configured)
        } else {
            // Count number of configured remotes
            val remoteCount = rcloneConfig.split("[").size - 1
            getString(R.string.settings_rclone_summary_configured, remoteCount)
        }
        binding.textRcloneStatus.text = statusText
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}

