package org.dydlakcloud.resticopia

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.wifi.WifiManager
import org.dydlakcloud.resticopia.config.FolderConfig
import java.time.ZonedDateTime

class BackupService : JobService() {
    companion object {
        private const val BACKUP_JOB_ID = 0

        /**
         * Reschedules the backup job with current preferences.
         * Cancels existing job and creates a new one with updated constraints.
         */
        fun reschedule(context: Context) {
            val jobScheduler = context.getSystemService(JobScheduler::class.java)
            jobScheduler.cancel(BACKUP_JOB_ID)
            scheduleInternal(context, jobScheduler)
        }

        /**
         * Schedules the backup job if not already scheduled.
         */
        fun schedule(context: Context) {
            val jobScheduler = context.getSystemService(JobScheduler::class.java)
            
            // Check if job already exists
            val contains = jobScheduler.allPendingJobs.any { job ->
                job.id == BACKUP_JOB_ID && job.service.className == BackupService::class.java.name
            }
            
            if (!contains) {
                scheduleInternal(context, jobScheduler)
            }
        }

        /**
         * Internal method to actually schedule the job with current preferences.
         */
        private fun scheduleInternal(context: Context, jobScheduler: JobScheduler) {
            val serviceComponent = ComponentName(context, BackupService::class.java)
            val builder = JobInfo.Builder(BACKUP_JOB_ID, serviceComponent)
            
            // Set periodic execution: every hour with 30 second flex window
            builder.setPersisted(true)
            builder.setPeriodic(60 * 60 * 1000L, 30 * 1000L)

            // Apply network constraint based on cellular preference
            val allowCellular = BackupPreferences.allowsCellular(context)
            if (allowCellular) {
                // Allow any network (WiFi or cellular)
                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            } else {
                // WiFi only (unmetered network)
                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            }

            // Apply charging constraint
            val requireCharging = BackupPreferences.requiresCharging(context)
            builder.setRequiresCharging(requireCharging)

            jobScheduler.schedule(builder.build())
        }

        fun startBackup(context: Context, callback: (() -> Unit)? = null) {
            val backupManager = BackupManager.instance(context)

            fun nextFolder(folders: List<FolderConfig>, callback: (() -> Unit)? = null) {
                if (folders.isEmpty()) {
                    if (callback != null) callback()
                } else {
                    val folder = folders.first()
                    fun next() = nextFolder(folders.drop(1), callback)

                    val now = ZonedDateTime.now()

                    val started =
                        folder.shouldBackup(now) && backupManager.backup(
                            context,
                            folder,
                            removeOld = true,
                            scheduled = true
                        ) {
                            next()
                        } != null
                    if (!started) {
                        next()
                    }
                }
            }

            nextFolder(backupManager.config.folders, callback)
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE)
                as? WifiManager
            ?: throw IllegalStateException("Could not get system Context.WIFI_SERVICE")
        val createWifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "restic")
        createWifiLock.setReferenceCounted(false)
        createWifiLock.acquire()

        startBackup(applicationContext) {
            createWifiLock.release()
            jobFinished(params, false)
        }

        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        val backupManager = BackupManager.instance(applicationContext)
        backupManager.currentlyActiveBackups().forEach { it.cancel() }

        // Wait for all backups to be cancelled to make sure the notification is dismissed
        while (backupManager.currentlyActiveBackups().isNotEmpty()) {
            Thread.sleep(100)
        }
        return true
    }
}