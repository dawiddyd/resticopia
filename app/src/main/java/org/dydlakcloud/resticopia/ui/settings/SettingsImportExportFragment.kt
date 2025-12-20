package org.dydlakcloud.resticopia.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import org.dydlakcloud.resticopia.BackupManager
import org.dydlakcloud.resticopia.BackupPreferences
import org.dydlakcloud.resticopia.BackupService
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.config.Config
import org.dydlakcloud.resticopia.util.ErrorHandler
import org.dydlakcloud.resticopia.config.PortableConfig
import org.dydlakcloud.resticopia.databinding.FragmentSettingsImportExportBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Import/Export Settings Fragment
 * 
 * Manages configuration backup and restore:
 * - Export encrypted settings file
 * - Import and validate settings
 * - Automatic configuration backup before import
 */
class SettingsImportExportFragment : Fragment() {

    private var _binding: FragmentSettingsImportExportBinding? = null
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
        _binding = FragmentSettingsImportExportBinding.inflate(inflater, container, false)
        _backupManager = BackupManager.instance(requireContext())

        setupButtons()

        return binding.root
    }

    private fun setupButtons() {
        binding.buttonExportSettings.setOnClickListener {
            exportSettings()
        }

        binding.buttonImportSettings.setOnClickListener {
            showImportConfirmationDialog()
        }
    }

    private fun exportSettings() {
        try {
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
            val errorHandler = ErrorHandler(requireContext())
            val userFriendlyError = errorHandler.getUserFriendlyError(e)
            showErrorDialog(userFriendlyError)
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
            
            val requiresCharging = BackupPreferences.requiresCharging(requireContext())
            val allowsCellular = BackupPreferences.allowsCellular(requireContext())
            
            val portableConfig = PortableConfig.fromConfig(
                config, 
                password, 
                requiresCharging, 
                allowsCellular
            )
            val jsonString = portableConfig.toJsonString()
            
            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            
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
            val errorHandler = ErrorHandler(requireContext())
            val userFriendlyError = errorHandler.getUserFriendlyError(e)
            showErrorDialog(userFriendlyError)
        }
    }

    private fun importSettingsFromUri(uri: Uri) {
        try {
            val jsonString = requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes().toString(Charsets.UTF_8)
            } ?: throw Exception("Failed to read file")
            
            val portableConfig = try {
                PortableConfig.fromJsonString(jsonString)
            } catch (e: Exception) {
                throw Exception("Invalid config format. Please export settings again using the latest version.")
            }
            
            if (portableConfig.version != 2) {
                throw Exception("Unsupported config version. Please export settings again.")
            }
            
            if (!portableConfig.encrypted) {
                throw Exception("This export format is not supported. Please export settings again with a password.")
            }
            
            showPasswordDialog(portableConfig)

        } catch (e: Exception) {
            e.printStackTrace()
            val errorHandler = ErrorHandler(requireContext())
            val userFriendlyError = errorHandler.getUserFriendlyError(e)
            showErrorDialog(userFriendlyError)
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
            
            val validationResult = validateConfig(config)
            
            if (validationResult.hasErrors()) {
                showValidationDialog(validationResult, config, portableConfig)
            } else {
                performImport(config, portableConfig)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val errorHandler = ErrorHandler(requireContext())
            val userFriendlyError = errorHandler.getUserFriendlyError(e)
            showErrorDialog(userFriendlyError)
        }
    }

    private fun validateConfig(config: Config): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val missingFolders = mutableListOf<String>()
        val invalidPaths = mutableListOf<String>()
        val orphanedFolders = mutableListOf<String>()
        
        config.folders.forEach { folder ->
            val path = folder.path
            
            if (!path.exists()) {
                missingFolders.add(path.absolutePath)
            }
            
            if (!path.isAbsolute) {
                invalidPaths.add(path.path)
            }
            
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

    private fun showValidationDialog(validationResult: ValidationResult, config: Config, portableConfig: PortableConfig) {
        val message = buildString {
            validationResult.issues.forEach { issue ->
                append(issue.summary)
                append("\n")
            }
            append("\n")
            
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
                performImport(config, portableConfig)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun performImport(config: Config, portableConfig: PortableConfig) {
        try {
            val backupConfig = backupManager.config
            val backupJson = backupConfig.toJsonString()
            val backupFile = requireContext().filesDir.resolve("config.backup.json")
            backupFile.writeText(backupJson, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        BackupPreferences.setRequiresCharging(requireContext(), portableConfig.requiresCharging)
        BackupPreferences.setAllowsCellular(requireContext(), portableConfig.allowsCellular)
        
        BackupService.reschedule(requireContext())
        
        backupManager.configure { _ ->
            config
        }.handle { _, throwable ->
            if (throwable != null) {
                throwable.printStackTrace()
                activity?.runOnUiThread {
                    val errorHandler = ErrorHandler(requireContext())
                    val userFriendlyError = errorHandler.getUserFriendlyError(throwable)
                    showErrorDialog(userFriendlyError)
                }
            } else {
                activity?.runOnUiThread {
                    showToast(getString(R.string.toast_import_success))
                    activity?.recreate()
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // Data classes for validation
    private data class ValidationResult(val issues: List<ValidationIssue>) {
        fun hasErrors(): Boolean = issues.isNotEmpty()
    }

    private enum class ValidationIssueType {
        MISSING_FOLDERS,
        INVALID_PATHS,
        ORPHANED_FOLDERS
    }

    private data class ValidationIssue(
        val type: ValidationIssueType,
        val summary: String,
        val details: List<String>
    )

    private fun showErrorDialog(userFriendlyError: ErrorHandler.UserFriendlyError) {
        AlertDialog.Builder(requireContext())
            .setTitle(userFriendlyError.title)
            .setMessage(userFriendlyError.message)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.error_show_technical_details) { _, _ ->
                showTechnicalDetailsDialog(userFriendlyError)
            }
            .show()
    }

    private fun showTechnicalDetailsDialog(userFriendlyError: ErrorHandler.UserFriendlyError) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.error_show_technical_details))
            .setMessage(userFriendlyError.originalError)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.button_copy) { _, _ ->
                // Copy technical details to clipboard
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Technical Error Details", userFriendlyError.originalError)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Technical details copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}

