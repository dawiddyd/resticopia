package org.dydlakcloud.resticopia.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.databinding.FragmentSettingsMainBinding

/**
 * Main Settings Fragment - displays categorized settings tiles
 * 
 * Follows Material Design patterns and app design system:
 * - Rounded tiles (16dp radius)
 * - Custom background color (#E8E5EF)
 * - Touch feedback with ripple effect
 * - Clean separation of concerns
 */
class SettingsMainFragment : Fragment() {

    private var _binding: FragmentSettingsMainBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsMainBinding.inflate(inflater, container, false)
        
        setupTileClickListeners()
        setupVersionInfo()
        
        return binding.root
    }
    
    /**
     * Display the app version
     */
    private fun setupVersionInfo() {
        val versionName = org.dydlakcloud.resticopia.BuildConfig.VERSION_NAME
        binding.textAppVersion.text = "Version $versionName"
    }

    /**
     * Configure click listeners for all settings tiles
     * Each tile navigates to its corresponding settings category
     */
    private fun setupTileClickListeners() {
        binding.tileNetwork.setOnClickListener {
            findNavController().navigate(R.id.action_settings_main_to_network)
        }

        binding.tileNotifications.setOnClickListener {
            findNavController().navigate(R.id.action_settings_main_to_notifications)
        }

        binding.tileBackupRules.setOnClickListener {
            findNavController().navigate(R.id.action_settings_main_to_backup_rules)
        }

        binding.tileRclone.setOnClickListener {
            findNavController().navigate(R.id.action_settings_main_to_rclone)
        }

        binding.tileImportExport.setOnClickListener {
            findNavController().navigate(R.id.action_settings_main_to_import_export)
        }

        binding.tileUtilities.setOnClickListener {
            findNavController().navigate(R.id.action_settings_main_to_utilities)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

