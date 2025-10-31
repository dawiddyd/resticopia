package org.dydlakcloud.resticopia.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.dydlakcloud.resticopia.BackupManager
import org.dydlakcloud.resticopia.databinding.FragmentSettingsNetworkBinding
import org.dydlakcloud.resticopia.ui.InputDialogUtil
import org.dydlakcloud.resticopia.util.HostnameUtil

/**
 * Network Settings Fragment
 * 
 * Manages network-related settings:
 * - Device hostname configuration
 * - DNS server settings
 */
class SettingsNetworkFragment : Fragment() {

    private var _binding: FragmentSettingsNetworkBinding? = null
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsNetworkBinding.inflate(inflater, container, false)
        _backupManager = BackupManager.instance(requireContext())

        setupHostnameSettings()
        setupDnsSettings()

        return binding.root
    }

    private fun setupHostnameSettings() {
        val restic = backupManager.restic
        binding.textHostname.text = restic.hostname

        binding.buttonHostnameEdit.setOnClickListener {
            InputDialogUtil.showInputTextDialog(
                requireContext(),
                requireView(),
                "Hostname",
                binding.textHostname.text.toString()
            ) { hostname ->
                binding.textHostname.text = backupManager.setHostname(
                    if (hostname.isBlank()) null
                    else hostname.trim()
                ) {
                    HostnameUtil.detectHostname(requireContext())
                }
            }
        }
    }

    private fun setupDnsSettings() {
        val restic = backupManager.restic
        binding.textDns.text = restic.nameServers.nameServers().joinToString(", ")

        binding.buttonDnsEdit.setOnClickListener {
            InputDialogUtil.showInputTextDialog(
                requireContext(),
                requireView(),
                "DNS Server",
                binding.textDns.text.toString()
            ) { nameServersString ->
                val nameServers =
                    if (nameServersString.isBlank()) emptyList()
                    else nameServersString.trim().split("\\s*,\\s*".toRegex())
                binding.textDns.text = backupManager.setNameServers(
                    nameServers.ifEmpty { null },
                    requireContext()
                ).nameServers().joinToString(", ")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}

