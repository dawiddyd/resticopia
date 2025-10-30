package org.dydlakcloud.resticopia.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import org.dydlakcloud.resticopia.BackupManager
import org.dydlakcloud.resticopia.databinding.FragmentSettingsNotificationsBinding
import org.dydlakcloud.resticopia.notification.NtfyNotifier
import org.dydlakcloud.resticopia.ui.InputDialogUtil

/**
 * Notifications Settings Fragment
 * 
 * Manages notification settings:
 * - Ntfy topic configuration
 * - Test notification functionality
 */
class SettingsNotificationsFragment : Fragment() {

    private var _binding: FragmentSettingsNotificationsBinding? = null
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsNotificationsBinding.inflate(inflater, container, false)
        _backupManager = BackupManager.instance(requireContext())

        setupNtfySettings()
        setupTestNotification()

        return binding.root
    }

    private fun setupNtfySettings() {
        binding.textNtfy.text = backupManager.config.ntfyUrl ?: ""

        binding.buttonNtfyEdit.setOnClickListener {
            InputDialogUtil.showInputTextDialog(
                requireContext(),
                requireView(),
                "Ntfy URL",
                binding.textNtfy.text.toString()
            ) { ntfyUrl ->
                backupManager.configure { config ->
                    config.copy(ntfyUrl = if (ntfyUrl.isBlank()) null else ntfyUrl.trim())
                }.thenAccept {
                    activity?.runOnUiThread {
                        binding.textNtfy.text = backupManager.config.ntfyUrl ?: ""
                    }
                }
            }
        }
    }

    private fun setupTestNotification() {
        binding.buttonTestNotification.setOnClickListener {
            val ntfyUrl = backupManager.config.ntfyUrl
            
            if (ntfyUrl.isNullOrBlank()) {
                Toast.makeText(
                    requireContext(),
                    "Please configure Ntfy URL first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            binding.buttonTestNotification.isEnabled = false
            
            NtfyNotifier.sendNotification(ntfyUrl, "Test notification from Resticopia")
                .thenAccept {
                    activity?.runOnUiThread {
                        binding.buttonTestNotification.isEnabled = true
                        Toast.makeText(
                            requireContext(),
                            "Test notification sent successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .exceptionally { throwable ->
                    activity?.runOnUiThread {
                        binding.buttonTestNotification.isEnabled = true
                        Toast.makeText(
                            requireContext(),
                            "Failed to send notification: ${throwable.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    null
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}

