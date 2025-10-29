package org.dydlakcloud.resticopia.ui.settings

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
import org.dydlakcloud.resticopia.BackupManager
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.BackupPreferences
import org.dydlakcloud.resticopia.BackupService
import org.dydlakcloud.resticopia.config.Config
import org.dydlakcloud.resticopia.config.PortableConfig
import org.dydlakcloud.resticopia.databinding.FragmentSettingsBinding
import org.dydlakcloud.resticopia.notification.NtfyNotifier
import android.widget.EditText
import org.dydlakcloud.resticopia.ui.InputDialogUtil
import org.dydlakcloud.resticopia.util.DirectoryChooser
import org.dydlakcloud.resticopia.util.HostnameUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import org.dydlakcloud.resticopia.config.FolderConfig
import org.dydlakcloud.resticopia.ui.folder.FolderEditFragment
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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

    // Activity result launcher for rclone config editor
    private val rcloneConfigEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val newConfig = result.data?.getStringExtra("config")
            newConfig?.let { configContent ->
                // Validate and save
                backupManager.configure { config ->
                    config.copy(rcloneConfig = configContent)
                }
                // Reinitialize Restic with the new config so rcloneConfig is available
                backupManager.initRestic(requireContext())
                updateRcloneStatus()
                Toast.makeText(
                    requireContext(), 
                    R.string.rclone_config_saved, 
                    Toast.LENGTH_SHORT
                ).show()
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

        binding.buttonNtfyUrlEdit.setOnClickListener {
            InputDialogUtil.showInputTextDialog(
                requireContext(),
                requireView(),
                requireContext().resources.getString(org.dydlakcloud.resticopia.R.string.ntfy_url_title),
                backupManager.config.ntfyUrl ?: ""
            ) { ntfyUrl ->
                backupManager.configure { config ->
                    config.copy(ntfyUrl = if (ntfyUrl.isBlank()) null else ntfyUrl.trim())
                }.thenAccept {
                    requireActivity().runOnUiThread {
                        val displayText = if (backupManager.config.ntfyUrl.isNullOrBlank()) {
                            requireContext().resources.getString(org.dydlakcloud.resticopia.R.string.ntfy_url_empty)
                        } else {
                            backupManager.config.ntfyUrl
                        }
                        binding.textNtfyUrl.text = displayText
                    }
                }
            }
        }

        binding.textNtfyUrl.text = if (backupManager.config.ntfyUrl.isNullOrBlank()) {
            requireContext().resources.getString(org.dydlakcloud.resticopia.R.string.ntfy_url_empty)
        } else {
            backupManager.config.ntfyUrl
        }

        binding.buttonNtfyTest.setOnClickListener {
            val ntfyUrl = backupManager.config.ntfyUrl
            
            if (ntfyUrl.isNullOrBlank()) {
                Toast.makeText(
                    requireContext(),
                    "Please configure ntfy URL first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Disable button while sending
            binding.buttonNtfyTest.isEnabled = false
            
            val testMessage = requireContext().resources.getString(R.string.ntfy_test_message)
            
            NtfyNotifier.sendNotification(ntfyUrl, testMessage)
                .thenAccept {
                    requireActivity().runOnUiThread {
                        binding.buttonNtfyTest.isEnabled = true
                        Toast.makeText(
                            requireContext(),
                            "Test notification sent!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .exceptionally { throwable ->
                    requireActivity().runOnUiThread {
                        binding.buttonNtfyTest.isEnabled = true
                        Toast.makeText(
                            requireContext(),
                            "Failed to send notification: ${throwable.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    null
                }
        }

        // Initialize backup constraint checkboxes
        binding.checkboxRequireCharging.isChecked = BackupPreferences.requiresCharging(context)
        binding.checkboxAllowCellular.isChecked = BackupPreferences.allowsCellular(context)

        // Handle checkbox changes for require charging
        binding.checkboxRequireCharging.setOnCheckedChangeListener { _, isChecked ->
            BackupPreferences.setRequiresCharging(context, isChecked)
            // Reschedule backup service with new constraints
            BackupService.reschedule(context)
        }

        // Handle checkbox changes for allow cellular
        binding.checkboxAllowCellular.setOnCheckedChangeListener { _, isChecked ->
            BackupPreferences.setAllowsCellular(context, isChecked)
            // Reschedule backup service with new constraints
            BackupService.reschedule(context)
        }

        // Handle view queued backups button
        binding.buttonViewQueuedBackups.setOnClickListener {
            showQueuedBackupsDialog()
        }

        // Initialize rclone status
        updateRcloneStatus()
        
        // Handle rclone configure button
        binding.buttonRcloneConfigure.setOnClickListener {
            val intent = Intent(requireContext(), RcloneConfigEditorActivity::class.java)
            intent.putExtra("config", backupManager.config.rcloneConfig ?: "")
            rcloneConfigEditorLauncher.launch(intent)
        }

        // Handle utilities expand/collapse
        var utilitiesExpanded = false
        binding.textUtilsDescription.setOnClickListener {
            utilitiesExpanded = !utilitiesExpanded
            binding.utilsButtonsContainer.visibility = if (utilitiesExpanded) View.VISIBLE else View.GONE
            binding.textUtilsDescription.text = getString(
                if (utilitiesExpanded) R.string.settings_utilities_expanded
                else R.string.settings_utilities_collapsed
            )
        }

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

    /**
     * Checks if the device is currently charging.
     */
    private fun isDeviceCharging(context: Context): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return batteryManager?.isCharging ?: false
    }

    /**
     * Checks if the device is currently connected to WiFi (unmetered network).
     */
    private fun isConnectedToWiFi(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            // Check if connected to WiFi or if network is unmetered
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                   !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).not()
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo?.type == ConnectivityManager.TYPE_WIFI
        }
    }

    /**
     * Data class to hold information about a queued backup.
     */
    private data class QueuedBackupInfo(
        val folder: FolderConfig,
        val waitingForCharging: Boolean,
        val waitingForWiFi: Boolean,
        val repoName: String,
        val overdueDuration: String
    )

    /**
     * Calculates and formats how long a backup is overdue.
     * Returns a human-readable string like "23 hours" or "2 days 5 hours"
     */
    private fun calculateOverdueDuration(folder: FolderConfig, now: ZonedDateTime): String {
        val scheduleMinutes = FolderEditFragment.schedules.find { it.first == folder.schedule }?.second
            ?: return "Unknown"
        
        val lastBackup = folder.lastBackup(filterScheduled = true)?.timestamp
        if (lastBackup == null) {
            return "Never backed up"
        }
        
        // Calculate when the next backup should have occurred
        var quantized = lastBackup.withMinute(0).withSecond(0).withNano(0)
        if (scheduleMinutes >= 24 * 60) {
            quantized = quantized.withHour(0)
        }
        val nextBackupShouldHave = quantized.plusMinutes(scheduleMinutes.toLong())
        
        // Calculate how long overdue
        val overdueDuration = java.time.Duration.between(nextBackupShouldHave, now)
        
        if (overdueDuration.isNegative || overdueDuration.isZero) {
            return "Due now"
        }
        
        val days = overdueDuration.toDays()
        val hours = overdueDuration.toHours() % 24
        val minutes = overdueDuration.toMinutes() % 60
        
        return when {
            days > 0 && hours > 0 -> "$days day${if (days > 1) "s" else ""} $hours hour${if (hours > 1) "s" else ""}"
            days > 0 -> "$days day${if (days > 1) "s" else ""}"
            hours > 0 -> "$hours hour${if (hours > 1) "s" else ""}"
            minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""}"
            else -> "Less than a minute"
        }
    }

    /**
     * Shows a dialog with all backups that are queued/waiting for constraints to be met.
     */
    private fun showQueuedBackupsDialog() {
        val context = requireContext()
        val config = backupManager.config
        val now = ZonedDateTime.now()
        
        // Check current device state
        val isCharging = isDeviceCharging(context)
        val isOnWiFi = isConnectedToWiFi(context)
        
        // Get user preferences
        val requireCharging = BackupPreferences.requiresCharging(context)
        val allowCellular = BackupPreferences.allowsCellular(context)
        
        // Find all folders that should backup now but are waiting for constraints
        val queuedBackups = mutableListOf<QueuedBackupInfo>()
        
        config.folders.forEach { folder ->
            // Check if this folder has a schedule and should backup
            val scheduleMinutes = FolderEditFragment.schedules.find { it.first == folder.schedule }?.second
            if (scheduleMinutes != null && scheduleMinutes >= 0 && folder.shouldBackup(now)) {
                val repo = folder.repo(config)
                val repoName = repo?.base?.name ?: "Unknown"
                
                // Check which constraints are blocking this backup
                val waitingForCharging = requireCharging && !isCharging
                val waitingForWiFi = !allowCellular && !isOnWiFi
                
                // Only add if at least one constraint is not met
                if (waitingForCharging || waitingForWiFi) {
                    val overdueDuration = calculateOverdueDuration(folder, now)
                    queuedBackups.add(QueuedBackupInfo(
                        folder = folder,
                        waitingForCharging = waitingForCharging,
                        waitingForWiFi = waitingForWiFi,
                        repoName = repoName,
                        overdueDuration = overdueDuration
                    ))
                }
            }
        }
        
        // Show appropriate dialog
        if (queuedBackups.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle(R.string.dialog_queued_backups_title)
                .setMessage(R.string.dialog_no_queued_backups)
                .setPositiveButton(R.string.button_ok, null)
                .show()
        } else {
            // Build detailed message
            val messageBuilder = StringBuilder()
            messageBuilder.append(getString(R.string.dialog_queued_backups_message))
            messageBuilder.append("\n\n")
            
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            
            queuedBackups.forEachIndexed { index, queuedBackup ->
                messageBuilder.append("${index + 1}. ")
                messageBuilder.append("${queuedBackup.folder.path}\n")
                messageBuilder.append("   Repository: ${queuedBackup.repoName}\n")
                messageBuilder.append("   Schedule: ${queuedBackup.folder.schedule}\n")
                
                // Show last backup info
                val lastBackup = queuedBackup.folder.lastBackup(filterScheduled = true)
                if (lastBackup != null) {
                    val formattedDate = lastBackup.timestamp.format(dateFormatter)
                    val status = if (lastBackup.successful) "✓" else "✗"
                    messageBuilder.append("   Last Backup: $formattedDate $status\n")
                } else {
                    messageBuilder.append("   Last Backup: Never\n")
                }
                
                // Show overdue duration
                messageBuilder.append("   ${getString(R.string.backup_overdue_by, queuedBackup.overdueDuration)}\n")
                
                // Show which constraints are blocking
                messageBuilder.append("   Status:\n")
                if (queuedBackup.waitingForCharging) {
                    messageBuilder.append("      ${getString(R.string.constraint_waiting_charging)}\n")
                }
                if (queuedBackup.waitingForWiFi) {
                    messageBuilder.append("      ${getString(R.string.constraint_waiting_wifi)}\n")
                }
                
                if (index < queuedBackups.size - 1) {
                    messageBuilder.append("\n")
                }
            }
            
            AlertDialog.Builder(context)
                .setTitle(R.string.dialog_queued_backups_title)
                .setMessage(messageBuilder.toString())
                .setPositiveButton(R.string.button_ok, null)
                .show()
        }
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
            
            // Read backup constraints from SharedPreferences
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
                showValidationDialog(validationResult, config, portableConfig)
            } else {
                performImport(config, portableConfig)
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

    private fun showValidationDialog(validationResult: ValidationResult, config: Config, portableConfig: PortableConfig) {
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
                performImport(config, portableConfig)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun performImport(config: Config, portableConfig: PortableConfig) {
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
        
        // Apply backup constraints to SharedPreferences
        BackupPreferences.setRequiresCharging(requireContext(), portableConfig.requiresCharging)
        BackupPreferences.setAllowsCellular(requireContext(), portableConfig.allowsCellular)
        
        // Update checkbox UI immediately
        activity?.runOnUiThread {
            binding.checkboxRequireCharging.isChecked = portableConfig.requiresCharging
            binding.checkboxAllowCellular.isChecked = portableConfig.allowsCellular
        }
        
        // Reschedule backup service to apply the new constraints
        BackupService.reschedule(requireContext())
        
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

    private fun updateRcloneStatus() {
        val rcloneConfig = backupManager.config.rcloneConfig
        if (rcloneConfig.isNullOrBlank()) {
            binding.textRcloneStatus.text = getString(R.string.settings_rclone_summary_not_configured)
        } else {
            try {
                val remotes = org.dydlakcloud.resticopia.util.RcloneConfigParser.parseConfigContent(rcloneConfig)
                binding.textRcloneStatus.text = getString(
                    R.string.settings_rclone_summary_configured, 
                    remotes.size
                )
            } catch (e: Exception) {
                binding.textRcloneStatus.text = getString(R.string.settings_rclone_summary_not_configured)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}