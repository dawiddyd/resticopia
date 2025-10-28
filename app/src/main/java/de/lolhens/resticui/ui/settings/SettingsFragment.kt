package de.lolhens.resticui.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import de.lolhens.resticui.BackupManager
import de.lolhens.resticui.R
import de.lolhens.resticui.BackupPreferences
import de.lolhens.resticui.BackupService
import de.lolhens.resticui.databinding.FragmentSettingsBinding
import de.lolhens.resticui.notification.NtfyNotifier
import de.lolhens.resticui.ui.InputDialogUtil
import de.lolhens.resticui.util.DirectoryChooser
import de.lolhens.resticui.util.HostnameUtil
import android.content.Context
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.appcompat.app.AlertDialog
import de.lolhens.resticui.config.FolderConfig
import de.lolhens.resticui.ui.folder.FolderEditFragment
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

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
                requireContext().resources.getString(de.lolhens.resticui.R.string.ntfy_url_title),
                backupManager.config.ntfyUrl ?: ""
            ) { ntfyUrl ->
                backupManager.configure { config ->
                    config.copy(ntfyUrl = if (ntfyUrl.isBlank()) null else ntfyUrl.trim())
                }.thenAccept {
                    requireActivity().runOnUiThread {
                        val displayText = if (backupManager.config.ntfyUrl.isNullOrBlank()) {
                            requireContext().resources.getString(de.lolhens.resticui.R.string.ntfy_url_empty)
                        } else {
                            backupManager.config.ntfyUrl
                        }
                        binding.textNtfyUrl.text = displayText
                    }
                }
            }
        }

        binding.textNtfyUrl.text = if (backupManager.config.ntfyUrl.isNullOrBlank()) {
            requireContext().resources.getString(de.lolhens.resticui.R.string.ntfy_url_empty)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}