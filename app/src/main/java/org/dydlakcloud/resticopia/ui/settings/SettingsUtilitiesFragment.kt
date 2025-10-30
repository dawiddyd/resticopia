package org.dydlakcloud.resticopia.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.dydlakcloud.resticopia.BackupManager
import org.dydlakcloud.resticopia.databinding.FragmentSettingsUtilitiesBinding
import org.dydlakcloud.resticopia.util.DirectoryChooser

/**
 * Utilities Settings Fragment
 * 
 * Manages utility functions:
 * - Download directory configuration
 * - Unlock all repositories
 * - Clean restic cache
 */
class SettingsUtilitiesFragment : Fragment() {

    private var _binding: FragmentSettingsUtilitiesBinding? = null
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    private lateinit var directoryChooser: DirectoryChooser

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsUtilitiesBinding.inflate(inflater, container, false)
        _backupManager = BackupManager.instance(requireContext())

        setupDownloadDirectory()
        setupUtilityButtons()

        return binding.root
    }

    private fun setupDownloadDirectory() {
        val sharedPref = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        binding.textDl.text = sharedPref?.getString("dl_path", "") ?: ""

        directoryChooser = DirectoryChooser.newInstance()
        directoryChooser.register(this, requireContext()) { path ->
            val editor = sharedPref?.edit()
            editor?.putString("dl_path", path)
            editor?.apply()
            binding.textDl.text = path
        }

        binding.buttonDlEdit.setOnClickListener {
            directoryChooser.openDialog()
        }
    }

    private fun setupUtilityButtons() {
        binding.buttonUnlock.setOnClickListener {
            backupManager.config.repos.forEach { repo ->
                val resticRepo = repo.repo(backupManager.restic)
                resticRepo.unlock()
                    .handle { message, throwable ->
                        if (throwable != null) {
                            throwable.printStackTrace()
                        } else {
                            println(message)
                        }
                    }
            }
        }

        binding.buttonCleanup.setOnClickListener {
            backupManager.restic.cleanCache()
                .handle { message, throwable ->
                    if (throwable != null) {
                        throwable.printStackTrace()
                    } else {
                        println(message)
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}

