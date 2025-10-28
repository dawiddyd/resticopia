package de.lolhens.resticui.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import de.lolhens.resticui.BackupManager
import de.lolhens.resticui.R
import de.lolhens.resticui.config.Config
import de.lolhens.resticui.config.PortableConfig
import de.lolhens.resticui.databinding.FragmentSettingsBinding
import android.widget.EditText
import de.lolhens.resticui.ui.InputDialogUtil
import de.lolhens.resticui.util.DirectoryChooser
import de.lolhens.resticui.util.HostnameUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    // Activity result launcher for exporting settings
    private val exportSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                exportSettingsToUri(uri)
            }
        }
    }

    // Activity result launcher for importing settings
    private val importSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importSettingsFromUri(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        _backupManager = BackupManager.instance(requireContext())

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

        val context = requireContext()
        val sharedPref = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        binding.textDl.text = sharedPref?.getString("dl_path", "") ?: ""

        val directoryChooser = DirectoryChooser.newInstance()

        directoryChooser.register(this, requireContext()) { path ->
            val editor = sharedPref?.edit()
            editor?.putString("dl_path", path)
            editor?.apply()
            binding.textDl.text = path
        }

        binding.buttonDlEdit.setOnClickListener {
            directoryChooser.openDialog()
        }

        val restic = BackupManager.instance(requireContext()).restic

        binding.buttonHostnameEdit.setOnClickListener {
            InputDialogUtil.showInputTextDialog(
                requireContext(),
                requireView(),
                binding.textHostnameDescription.text.toString(),
                binding.textHostname.text.toString()
            ) { hostname ->
                binding.textHostname.text = BackupManager.instance(requireContext()).setHostname(
                    if (hostname.isBlank()) null
                    else hostname.trim()
                ) {
                    HostnameUtil.detectHostname(requireContext())
                }
            }
        }

        binding.textHostname.text = restic.hostname

        binding.buttonDnsEdit.setOnClickListener {
            InputDialogUtil.showInputTextDialog(
                requireContext(),
                requireView(),
                binding.textDnsDescription.text.toString(),
                binding.textDns.text.toString()
            ) { nameServersString ->
                val nameServers =
                    if (nameServersString.isBlank()) emptyList()
                    else nameServersString.trim().split("\\s*,\\s*".toRegex())
                binding.textDns.text = BackupManager.instance(requireContext()).setNameServers(
                    nameServers.ifEmpty { null },
                    requireContext()
                ).nameServers().joinToString(", ")
            }
        }

        binding.textDns.text = restic.nameServers.nameServers().joinToString(", ")

        // Export settings button
        binding.buttonExportSettings.setOnClickListener {
            exportSettings()
        }

        // Import settings button
        binding.buttonImportSettings.setOnClickListener {
            showImportConfirmationDialog()
        }

        return root
    }

    private fun exportSettings() {
        try {
            // Show password dialog
            val input = EditText(requireContext()).apply {
                hint = getString(R.string.hint_export_password)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or 
                           android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.alert_export_password_title)
                .setMessage(R.string.alert_export_password_message)
                .setView(input)
                .setPositiveButton(R.string.button_ok) { _, _ ->
                    val password = input.text.toString()
                    if (validatePassword(password)) {
                        startExportWithPassword(password)
                    }
                }
                .setNegativeButton(R.string.button_cancel, null)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(getString(R.string.toast_export_failed, e.message))
        }
    }

    private fun validatePassword(password: String): Boolean {
        return when {
            password.isEmpty() -> {
                showToast(getString(R.string.error_password_required))
                false
            }
            password.length < 8 -> {
                showToast(getString(R.string.error_password_too_short))
                false
            }
            else -> true
        }
    }

    private fun startExportWithPassword(password: String) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val fileName = "restic-settings-${dateFormat.format(Date())}.json"
            
            // Store password temporarily for use in the launcher callback
            requireActivity().intent.putExtra("export_password", password)
            
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
            
            exportSettingsLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(getString(R.string.toast_export_failed, e.message))
        }
    }

    private fun exportSettingsToUri(uri: Uri) {
        try {
            val password = requireActivity().intent.getStringExtra("export_password")
                ?: throw Exception("Password not provided")
            
            val config = backupManager.config
            val portableConfig = PortableConfig.fromConfig(config, password)
            val jsonString = portableConfig.toJsonString()
            
            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            
            // Clear temp data
            requireActivity().intent.removeExtra("export_password")
            
            showToast(getString(R.string.toast_export_success))
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(getString(R.string.toast_export_failed, e.message))
        }
    }

    private fun showImportConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.alert_import_settings_title)
            .setMessage(R.string.alert_import_settings_message)
            .setPositiveButton(R.string.button_ok) { _, _ ->
                importSettings()
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun importSettings() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            
            importSettingsLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(getString(R.string.toast_import_failed, e.message))
        }
    }

    private fun importSettingsFromUri(uri: Uri) {
        try {
            val jsonString = requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes().toString(Charsets.UTF_8)
            } ?: throw Exception("Failed to read file")
            
            // Parse as PortableConfig
            val portableConfig = try {
                PortableConfig.fromJsonString(jsonString)
            } catch (e: Exception) {
                throw Exception("Invalid config format. Please export settings again using the latest version.")
            }
            
            // Check if it's the correct version
            if (portableConfig.version != 2) {
                throw Exception("Unsupported config version. Please export settings again.")
            }
            
            // Must be encrypted
            if (!portableConfig.encrypted) {
                throw Exception("This export format is not supported. Please export settings again with a password.")
            }
            
            // Show password dialog
            showPasswordDialog(portableConfig)
            
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(getString(R.string.toast_import_failed, e.message))
        }
    }

    private fun showPasswordDialog(encryptedPortableConfig: PortableConfig) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.hint_import_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or 
                       android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.alert_import_password_title)
            .setMessage(R.string.alert_import_password_message)
            .setView(input)
            .setPositiveButton(R.string.button_ok) { _, _ ->
                val password = input.text.toString()
                decryptAndImport(encryptedPortableConfig, password)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .setCancelable(false)
            .show()
    }

    private fun decryptAndImport(encryptedPortableConfig: PortableConfig, password: String) {
        try {
            val portableConfig = encryptedPortableConfig.decrypt(password)
            val config = portableConfig.toConfig()
            
            // Validate the imported config
            val validationResult = validateConfig(config)
            
            if (validationResult.hasErrors()) {
                showValidationDialog(validationResult, config)
            } else {
                performImport(config)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(getString(R.string.toast_import_failed, "Invalid password or corrupted file"))
        }
    }

    private fun validateConfig(config: Config): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val missingFolders = mutableListOf<String>()
        val invalidPaths = mutableListOf<String>()
        val orphanedFolders = mutableListOf<String>()
        
        // Check for missing folders and invalid paths
        config.folders.forEach { folder ->
            val path = folder.path
            
            // Check if folder exists
            if (!path.exists()) {
                missingFolders.add(path.absolutePath)
            }
            
            // Check if path is readable (basic validation)
            if (!path.isAbsolute) {
                invalidPaths.add(path.path)
            }
            
            // Check for orphaned folders (referencing non-existent repos)
            if (config.repos.none { it.base.id == folder.repoId }) {
                orphanedFolders.add(path.absolutePath)
            }
        }
        
        if (missingFolders.isNotEmpty()) {
            issues.add(ValidationIssue(
                ValidationIssueType.MISSING_FOLDERS,
                getString(R.string.validation_missing_folders, missingFolders.size),
                missingFolders
            ))
        }
        
        if (invalidPaths.isNotEmpty()) {
            issues.add(ValidationIssue(
                ValidationIssueType.INVALID_PATHS,
                getString(R.string.validation_invalid_paths, invalidPaths.size),
                invalidPaths
            ))
        }
        
        if (orphanedFolders.isNotEmpty()) {
            issues.add(ValidationIssue(
                ValidationIssueType.ORPHANED_FOLDERS,
                getString(R.string.validation_orphaned_folders, orphanedFolders.size),
                orphanedFolders
            ))
        }
        
        return ValidationResult(issues)
    }

    private fun showValidationDialog(validationResult: ValidationResult, config: Config) {
        val message = buildString {
            validationResult.issues.forEach { issue ->
                append(issue.summary)
                append("\n")
            }
            append("\n")
            
            // Show details
            validationResult.issues.forEach { issue ->
                if (issue.details.isNotEmpty()) {
                    append("\n${issue.type.name}:\n")
                    issue.details.take(3).forEach { detail ->
                        append("  • $detail\n")
                    }
                    if (issue.details.size > 3) {
                        append("  ... and ${issue.details.size - 3} more\n")
                    }
                }
            }
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.alert_import_validation_title)
            .setMessage(getString(R.string.alert_import_validation_message, message))
            .setPositiveButton(R.string.button_ok) { _, _ ->
                performImport(config)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun performImport(config: Config) {
        // Create backup of current config before import
        try {
            val backupConfig = backupManager.config
            val backupJson = backupConfig.toJsonString()
            val backupFile = requireContext().filesDir.resolve("config.backup.json")
            backupFile.writeText(backupJson, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            // Continue even if backup fails
        }
        
        // Update the config using BackupManager
        backupManager.configure { _ ->
            config
        }.handle { _, throwable ->
            if (throwable != null) {
                throwable.printStackTrace()
                activity?.runOnUiThread {
                    showToast(getString(R.string.toast_import_failed, throwable.message))
                }
            } else {
                activity?.runOnUiThread {
                    showToast(getString(R.string.toast_import_success))
                    // Restart the activity to reload all data
                    activity?.recreate()
                }
            }
        }
    }

    // Data classes for validation
    private data class ValidationResult(val issues: List<ValidationIssue>) {
        fun hasErrors(): Boolean = issues.isNotEmpty()
    }

    private data class ValidationIssue(
        val type: ValidationIssueType,
        val summary: String,
        val details: List<String>
    )

    private enum class ValidationIssueType {
        MISSING_FOLDERS,
        INVALID_PATHS,
        ORPHANED_FOLDERS
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}