package org.dydlakcloud.resticopia

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.dydlakcloud.resticopia.config.FolderConfig
import org.dydlakcloud.resticopia.databinding.ActivityMainBinding
import org.dydlakcloud.resticopia.ui.folder.FolderEditFragment
import org.dydlakcloud.resticopia.util.PermissionManager
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: androidx.navigation.NavController
    private var hasQueuedBackups = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Toolbar
        setSupportActionBar(binding.toolbar)

        val navView: BottomNavigationView = binding.navView

        navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        setupActionBarWithNavController(
            navController,
            AppBarConfiguration(
                setOf(
                    R.id.navigation_folders,
                    R.id.navigation_repos,
                    R.id.navigation_settings,
                    R.id.navigation_about
                )
            )
        )
        navView.setupWithNavController(navController)

        // Update toolbar menu when navigation changes
        navController.addOnDestinationChangedListener { _, _, _ ->
            checkQueuedBackupsAndUpdateIcon()
            invalidateOptionsMenu()
        }
        
        // Initial check for queued backups
        checkQueuedBackupsAndUpdateIcon()

        val backupManager = BackupManager.instance(applicationContext)

        if (!PermissionManager.instance.hasStoragePermission(applicationContext, write = true)) {
            PermissionManager.instance.requestStoragePermission(this, write = true)
                .thenApply { granted ->
                    if (granted) {
                        backupManager.initRestic(applicationContext)
                    } else {
                        Toast.makeText(
                            this,
                            "Allow permission for storage access!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        BackupService.schedule(applicationContext)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Only show bell icon on folders and repos pages
        val currentDestination = navController.currentDestination?.id
        if (currentDestination == R.id.navigation_folders || currentDestination == R.id.navigation_repos) {
            menuInflater.inflate(R.menu.menu_main_toolbar, menu)
            
            // Update bell icon based on queued backups state
            val bellItem = menu.findItem(R.id.action_queued_backups)
            bellItem?.setIcon(
                if (hasQueuedBackups) R.drawable.ic_bell_notification_badge
                else R.drawable.ic_bell_notification
            )
            
            return true
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_queued_backups -> {
                showQueuedBackupsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionManager.instance.onRequestPermissionsResult(requestCode)
    }

    private fun checkQueuedBackupsAndUpdateIcon() {
        val queuedBackups = getQueuedBackups()
        hasQueuedBackups = queuedBackups.isNotEmpty()
        invalidateOptionsMenu()
    }

    private fun getQueuedBackups(): List<QueuedBackupInfo> {
        val backupManager = BackupManager.instance(applicationContext)
        val config = backupManager.config
        val now = ZonedDateTime.now()
        
        // Check current device state
        val isCharging = isDeviceCharging(this)
        val isOnWiFi = isConnectedToWiFi(this)
        
        // Get user preferences
        val requireCharging = BackupPreferences.requiresCharging(this)
        val allowCellular = BackupPreferences.allowsCellular(this)
        
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
        
        return queuedBackups
    }

    private fun showQueuedBackupsDialog() {
        val queuedBackups = getQueuedBackups()
        
        // Show appropriate dialog
        if (queuedBackups.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_queued_backups_title)
                .setMessage(R.string.dialog_no_queued_backups)
                .setPositiveButton(R.string.button_ok, null)
                .show()
        } else {
            val messageBuilder = StringBuilder()
            messageBuilder.append(getString(R.string.dialog_queued_backups_message))
            messageBuilder.append("\n\n")
            
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            
            queuedBackups.forEachIndexed { index, queuedBackup ->
                messageBuilder.append("${index + 1}. ")
                messageBuilder.append("${queuedBackup.folder.path}\n")
                messageBuilder.append("   Repository: ${queuedBackup.repoName}\n")
                messageBuilder.append("   Schedule: ${queuedBackup.folder.schedule}\n")
                
                val lastBackup = queuedBackup.folder.lastBackup(filterScheduled = true)
                if (lastBackup != null) {
                    val formattedDate = lastBackup.timestamp.format(dateFormatter)
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
            
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_queued_backups_title)
                .setMessage(messageBuilder.toString())
                .setPositiveButton(R.string.button_ok, null)
                .show()
        }
        
        // Update icon after showing dialog (state might have changed)
        checkQueuedBackupsAndUpdateIcon()
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
}