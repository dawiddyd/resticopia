package org.dydlakcloud.resticopia.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import org.dydlakcloud.resticopia.BackupManager
import org.dydlakcloud.resticopia.BackupPreferences
import org.dydlakcloud.resticopia.BackupService
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.config.FolderConfig
import org.dydlakcloud.resticopia.databinding.FragmentSettingsBackupRulesBinding
import org.dydlakcloud.resticopia.ui.Formatters
import org.dydlakcloud.resticopia.ui.folder.FolderEditFragment
import org.dydlakcloud.resticopia.util.GitIgnorePatternMatcher
import java.time.ZonedDateTime

/**
 * Backup Rules Settings Fragment
 * 
 * Manages backup scheduling and constraints:
 * - Require charging for backups
 * - Allow backups over cellular data
 * - View queued backups waiting for constraints
 * - Configure ignore patterns for file exclusion
 */
class SettingsBackupRulesFragment : Fragment() {

    private var _binding: FragmentSettingsBackupRulesBinding? = null
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    private val ignorePatternsEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("patterns")?.let { newPatterns ->
                backupManager.configure { config ->
                    config.copy(ignorePatterns = newPatterns.ifEmpty { null })
                }.handle { _, throwable ->
                    if (throwable != null) {
                        throwable.printStackTrace()
                    }
                    activity?.runOnUiThread {
                        updateIgnorePatternsStatus()
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
        _binding = FragmentSettingsBackupRulesBinding.inflate(inflater, container, false)
        _backupManager = BackupManager.instance(requireContext())

        setupBackupConstraints()
        setupQueuedBackupsButton()
        setupIgnorePatternsSection()

        return binding.root
    }
    
    override fun onResume() {
        super.onResume()
        updateIgnorePatternsStatus()
    }

    private fun setupBackupConstraints() {
        val context = requireContext()

        // Initialize checkbox states
        binding.checkboxRequireCharging.isChecked = BackupPreferences.requiresCharging(context)
        binding.checkboxAllowCellular.isChecked = BackupPreferences.allowsCellular(context)

        // Handle checkbox changes for require charging
        binding.checkboxRequireCharging.setOnCheckedChangeListener { _, isChecked ->
            BackupPreferences.setRequiresCharging(context, isChecked)
            BackupService.reschedule(context)
        }

        // Handle checkbox changes for allow cellular
        binding.checkboxAllowCellular.setOnCheckedChangeListener { _, isChecked ->
            BackupPreferences.setAllowsCellular(context, isChecked)
            BackupService.reschedule(context)
        }
    }

    private fun setupQueuedBackupsButton() {
        binding.buttonViewQueuedBackups.setOnClickListener {
            showQueuedBackupsDialog()
        }
    }
    
    private fun setupIgnorePatternsSection() {
        binding.buttonConfigureIgnorePatterns.setOnClickListener {
            openIgnorePatternsEditor()
        }
        updateIgnorePatternsStatus()
    }
    
    private fun openIgnorePatternsEditor() {
        val currentPatterns = backupManager.config.ignorePatterns ?: ""
        val intent = Intent(requireContext(), IgnorePatternsEditorActivity::class.java).apply {
            putExtra("patterns", currentPatterns)
        }
        ignorePatternsEditorLauncher.launch(intent)
    }
    
    private fun updateIgnorePatternsStatus() {
        val patterns = backupManager.config.ignorePatterns
        if (patterns.isNullOrEmpty()) {
            binding.textIgnorePatternsStatus.text = getString(R.string.settings_ignore_patterns_summary_not_configured)
        } else {
            val patternList = GitIgnorePatternMatcher.parsePatterns(patterns)
            val count = patternList.size
            binding.textIgnorePatternsStatus.text = getString(R.string.settings_ignore_patterns_summary_configured, count)
        }
    }

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
            val scheduleMinutes = FolderEditFragment.schedules.find { it.first == folder.schedule }?.second
            if (scheduleMinutes != null && scheduleMinutes >= 0 && folder.shouldBackup(now)) {
                val repo = folder.repo(config)
                val repoName = repo?.base?.name ?: "Unknown"
                
                val waitingForCharging = requireCharging && !isCharging
                val waitingForWiFi = !allowCellular && !isOnWiFi
                
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
            val messageBuilder = StringBuilder()
            messageBuilder.append(getString(R.string.dialog_queued_backups_message))
            messageBuilder.append("\n\n")
            
            queuedBackups.forEachIndexed { index, queuedBackup ->
                messageBuilder.append("${index + 1}. ")
                messageBuilder.append("${queuedBackup.folder.path}\n")
                messageBuilder.append("   Repository: ${queuedBackup.repoName}\n")
                messageBuilder.append("   Schedule: ${queuedBackup.folder.schedule}\n")

                val lastBackup = queuedBackup.folder.lastBackup(filterScheduled = true)
                if (lastBackup != null) {
                    val formattedDate = Formatters.dateTimeStatus(lastBackup.timestamp)
                    val status = if (lastBackup.successful) "✓" else "✗"
                    messageBuilder.append("   Last Backup: $formattedDate $status\n")
                } else {
                    messageBuilder.append("   Last Backup: Never\n")
                }
                
                messageBuilder.append("   ${getString(R.string.backup_overdue_by, queuedBackup.overdueDuration)}\n")
                
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

    private fun calculateOverdueDuration(folder: FolderConfig, now: ZonedDateTime): String {
        val scheduleMinutes = FolderEditFragment.schedules.find { it.first == folder.schedule }?.second
            ?: return "Unknown"
        
        val lastBackup = folder.lastBackup(filterScheduled = true)?.timestamp
        if (lastBackup == null) {
            return "Never backed up"
        }
        
        var quantized = lastBackup.withMinute(0).withSecond(0).withNano(0)
        if (scheduleMinutes >= 24 * 60) {
            quantized = quantized.withHour(0)
        }
        val nextBackupShouldHave = quantized.plusMinutes(scheduleMinutes.toLong())
        
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

    private fun isDeviceCharging(context: Context): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return batteryManager?.isCharging ?: false
    }

    private fun isConnectedToWiFi(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                   !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).not()
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo?.type == ConnectivityManager.TYPE_WIFI
        }
    }

    private data class QueuedBackupInfo(
        val folder: FolderConfig,
        val waitingForCharging: Boolean,
        val waitingForWiFi: Boolean,
        val repoName: String,
        val overdueDuration: String
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}

