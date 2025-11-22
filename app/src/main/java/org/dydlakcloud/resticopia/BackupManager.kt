package org.dydlakcloud.resticopia

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import org.dydlakcloud.resticopia.config.*
import org.dydlakcloud.resticopia.notification.NtfyNotifier
import org.dydlakcloud.resticopia.restic.Restic
import org.dydlakcloud.resticopia.restic.ResticBackupProgress
import org.dydlakcloud.resticopia.restic.ResticException
import org.dydlakcloud.resticopia.restic.ResticNameServers
import org.dydlakcloud.resticopia.restic.ResticStorage
import org.dydlakcloud.resticopia.ui.folder.FolderActivity
import org.dydlakcloud.resticopia.util.FolderDeletionManager
import org.dydlakcloud.resticopia.util.HostnameUtil
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.math.roundToInt

class BackupManager private constructor(context: Context) {
    companion object {
        private var _instance: BackupManager? = null

        fun instance(context: Context): BackupManager = _instance ?: BackupManager(context)
    }

    private fun notificationManager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val configManager: ConfigManager = ConfigManager(context)

    private val _config: MutableLiveData<Pair<Config, Runnable>> = MutableLiveData()
    val config: Config get() = _config.value!!.first
    fun observeConfig(lifecycleOwner: LifecycleOwner, f: (Config) -> Unit) =
        _config.observe(lifecycleOwner) { (config, _) -> f(config) }

    fun configure(f: (Config) -> Config): CompletableFuture<Unit> {
        val callback = CompletableFuture<Unit>()
        _config.postValue(Pair(f(config), Runnable {
            callback.complete(null)
        }))
        return callback
    }

    private lateinit var _restic: Restic
    val restic get() = _restic
    fun initRestic(context: Context) {
        val nameServers = config.nameServers
        val resticNameServers =
            if (nameServers != null)
                ResticNameServers.fromList(nameServers)
            else
                ResticNameServers.fromContext(context)
        _restic = Restic(
            ResticStorage.fromContext(context),
            hostname = config.hostname ?: HostnameUtil.detectHostname(context),
            nameServers = resticNameServers,
            rcloneConfig = config.rcloneConfig
        )
    }

    fun setHostname(hostname: String?, defaultHostname: () -> String): String {
        configure { config ->
            config.copy(hostname = hostname)
        }
        val hostname = hostname ?: defaultHostname()
        _restic = _restic.withHostname(hostname)
        return hostname
    }

    fun setNameServers(nameServers: List<String>?, context: Context): ResticNameServers {
        configure { config ->
            config.copy(nameServers = nameServers)
        }
        val nameServers =
            if (nameServers != null)
                ResticNameServers.fromList(nameServers)
            else
                ResticNameServers.fromContext(context)
        _restic = _restic.withNameServers(nameServers)
        return nameServers
    }

    val notificationChannelId = "RESTIC_BACKUP_PROGRESS"
    private var lastMillis = 0L

    private fun updateNotification(
        context: Context,
        folderConfigId: FolderConfigId,
        activeBackup: ActiveBackup,
        doneNotification: Boolean = true,
        errorNotification: Boolean = true
    ) {
        fun pendingIntent() = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            FolderActivity.intent(context, false, folderConfigId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        when {
            activeBackup.inProgress -> {
                // reduce number of notification updates
                val nowMillis = System.currentTimeMillis()
                if ((nowMillis - lastMillis) < 300)
                    return
                else
                    lastMillis = nowMillis

                val isDeleting = activeBackup.deletionInProgress
                val progress = if (isDeleting) "Deleting..." else activeBackup.progress?.percentDoneString() ?: "0%"

                val contentTitle = if (isDeleting) {
                    context.resources.getString(R.string.notification_deletion_started)
                } else {
                    "${context.resources.getString(R.string.notification_backup_progress_message)} $progress"
                }

                notificationManager(context).notify(
                    activeBackup.notificationId,
                    NotificationCompat.Builder(context, notificationChannelId)
                        .setContentIntent(pendingIntent())
                        .setSubText(progress)
                        .setContentTitle(contentTitle)
                        .setContentText(
                            if (activeBackup.progress == null) null
                            else "${activeBackup.progress.timeElapsedString()} elapsed"
                        )
                        .setSmallIcon(R.drawable.outline_cloud_24)
                        .setProgress(
                            100,
                            activeBackup.progress?.percentDone100()?.roundToInt() ?: 0,
                            activeBackup.isStarting()
                        )
                        .setOngoing(true)
                        .build()
                )
            }
            activeBackup.error != null && errorNotification -> {
                notificationManager(context).notify(
                    activeBackup.notificationId,
                    NotificationCompat.Builder(context, notificationChannelId)
                        .setContentIntent(pendingIntent())
                        .setContentTitle(
                            "${context.resources.getString(R.string.notification_backup_failed_message)}\n${activeBackup.error}"
                        )
                        .setSmallIcon(R.drawable.outline_cloud_error_24)
                        .build()
                )
            }
            activeBackup.summary != null && doneNotification -> {
                var contentTitle = ""
                var folderConfig: FolderConfig? = null
                for (folder in config.folders) {
                    if (folder.id == folderConfigId) {
                        val repo = folder.repo(config)
                        val repoName = repo?.base?.name ?: "Unknown"
                        contentTitle = "$repoName: ${folder.path}"
                        folderConfig = folder
                        break
                    }
                }

                val isDeletionCompleted = activeBackup.deletionResult?.success == true
                val isDeletionFailed = activeBackup.deletionResult?.success == false

                val details = when {
                    isDeletionCompleted -> "Backup and deletion completed successfully"
                    isDeletionFailed -> "Backup successful, deletion failed: ${activeBackup.error}"
                    activeBackup.progress == null -> ""
                    else -> {
                        val ofTotal =
                            if (activeBackup.progress.total_files != null) "/${activeBackup.progress.total_files}" else ""

                        val unmodifiedNewChanged = listOf(
                            if (activeBackup.summary.files_unmodified != 0L) "U:${activeBackup.summary.files_unmodified}" else "",
                            if (activeBackup.summary.files_new != 0L) "N:${activeBackup.summary.files_new}" else "",
                            if (activeBackup.summary.files_changed != 0L) "C:${activeBackup.summary.files_changed}" else ""
                        ).filter { it.isNotEmpty() }.joinToString("/")

                        listOf(
                            activeBackup.progress.timeElapsedString(),
                            "${activeBackup.progress.files_done}$ofTotal Files ($unmodifiedNewChanged)",
                            "${activeBackup.progress.bytesDoneString()}${if (activeBackup.progress.total_bytes != null) "/${activeBackup.progress.totalBytesString()}" else ""}"
                        ).joinToString(" | ")
                    }
                }

                val notificationText = if (folderConfig?.deleteContentsAfterBackup == true && !isDeletionCompleted && !isDeletionFailed) {
                    "$details | Deleting folder contents..."
                } else {
                    details
                }

                notificationManager(context).notify(
                    activeBackup.notificationId,
                    NotificationCompat.Builder(context, notificationChannelId)
                        .setContentIntent(pendingIntent())
                        .setSubText("100%")
                        .setContentTitle(contentTitle)
                        .setContentText(notificationText)
                        .setSmallIcon(if (isDeletionFailed) R.drawable.outline_cloud_error_24 else R.drawable.outline_cloud_done_24)
                        .build()
                )
            }
            else -> {
                notificationManager(context).cancel(activeBackup.notificationId)
            }
        }
    }

    init {
        _instance = this

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val title = context.resources.getString(R.string.notification_channel_backup)
            val channel = NotificationChannel(
                notificationChannelId,
                title,
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = title
            notificationManager(context).createNotificationChannel(channel)
        }

        _config.value = Pair(configManager.readConfig(context), Runnable { })
        _config.observeForever { (config, callback) ->
            configManager.writeConfig(context, config)
            callback.run()
        }

        initRestic(context)
    }

    private val activeBackupsLock = Object()
    private var _activeBackups: Map<FolderConfigId, MutableLiveData<ActiveBackup>> = emptyMap()

    fun currentlyActiveBackups(): List<ActiveBackup> =
        synchronized(activeBackupsLock) {
            _activeBackups.values.map { it.value }.filterNotNull()
        }

    fun activeBackup(folderId: FolderConfigId): MutableLiveData<ActiveBackup> =
        synchronized(activeBackupsLock) {
            val liveData = _activeBackups[folderId]
            if (liveData == null) {
                val liveData = MutableLiveData<ActiveBackup>()
                _activeBackups = _activeBackups.plus(Pair(folderId, liveData))
                liveData
            } else {
                liveData
            }
        }

    fun backup(
        context: Context,
        folder: FolderConfig,
        removeOld: Boolean,
        scheduled: Boolean,
        callback: (() -> Unit)? = null
    ): ActiveBackup? {
        val repo = folder.repo(config) ?: return null

        val resticRepo = repo.repo(restic)

        val activeBackupLiveData = activeBackup(folder.id)
        if (activeBackupLiveData.value?.inProgress == true) return null

        val activeBackup = ActiveBackup.create()
        activeBackupLiveData.postValue(activeBackup)

        updateNotification(context, folder.id, activeBackup)

        val beforeBackup = ZonedDateTime.now()

        resticRepo.backup(
            listOf(folder.path),
            { progress ->
                val activeBackupProgress = activeBackupLiveData.value!!.progress(progress)
                activeBackupLiveData.postValue(activeBackupProgress)
                updateNotification(context, folder.id, activeBackupProgress)
            },
            activeBackup.cancelFuture,
            config.ignorePatterns // Pass ignore patterns from config
        ).handle { summary, throwable ->
            val throwable =
                if (throwable == null) null
                else if (throwable is CompletionException && throwable.cause != null) throwable.cause
                else throwable

            val cancelled = throwable is ResticException && throwable.cancelled

            val errorMessage =
                if (throwable == null || cancelled) null
                else throwable.message

            val afterBackup = ZonedDateTime.now()

            val historyEntry = BackupHistoryEntry(
                timestamp = afterBackup,
                duration = Duration.ofMillis(
                    afterBackup.toInstant().toEpochMilli() - beforeBackup.toInstant().toEpochMilli()
                ),
                scheduled = scheduled,
                cancelled = cancelled,
                snapshotId = summary?.snapshot_id,
                errorMessage = errorMessage
            )

            configure { config ->
                config.copy(folders = config.folders.map { currentFolder ->
                    if (currentFolder.id == folder.id) currentFolder.plusHistoryEntry(historyEntry)
                    else currentFolder
                })
            }

            val finishedActiveBackup = activeBackupLiveData.value!!.finish(summary, errorMessage)
            activeBackupLiveData.postValue(finishedActiveBackup)
            updateNotification(context, folder.id, finishedActiveBackup)

            // Handle folder deletion after successful backup
            if (summary != null && errorMessage == null && folder.deleteContentsAfterBackup) {
                performFolderDeletion(context, folder, finishedActiveBackup)
            }

            // Send ntfy notification
            val ntfyUrl = config.ntfyUrl
            if (!cancelled && ntfyUrl != null) {
                if (errorMessage != null) {
                    NtfyNotifier.sendBackupFailureNotification(ntfyUrl, folder.path.absolutePath, errorMessage)
                } else if (summary != null) {
                    NtfyNotifier.sendBackupSuccessNotification(ntfyUrl, folder.path.absolutePath)
                }
            }

            fun removeOldBackups(callback: () -> Unit) {
                if (removeOld && throwable == null && (folder.keepLast != null || folder.keepWithin != null)) {
                    resticRepo.forget(
                        folder.keepLast,
                        folder.keepWithin,
                        prune = true
                    ).handle { _, _ ->
                        callback()
                    }
                } else {
                    callback()
                }
            }

            removeOldBackups {
                resticRepo.unlock().handle { _, _ ->
                    if (callback != null) callback()
                }
            }
        }

        return activeBackup
    }

    /**
     * Performs folder content deletion after successful backup
     */
    private fun performFolderDeletion(
        context: Context,
        folder: FolderConfig,
        activeBackup: ActiveBackup
    ) {
        // Show deletion started notification
        val deletionStartedNotification = activeBackup.copy(
            progress = ResticBackupProgress(percent_done = 50.0), // Show as 50% complete
            deletionInProgress = true
        )
        activeBackup(folder.id).postValue(deletionStartedNotification)
        updateNotification(context, folder.id, deletionStartedNotification, doneNotification = false)

        try {
            val deletionManager = FolderDeletionManager(context)
            val result = deletionManager.deleteFolderContents(folder)

            // Update notification with deletion result
            val finalNotification = if (result.success) {
                activeBackup.copy(
                    summary = activeBackup.summary,
                    progress = ResticBackupProgress(percent_done = 100.0),
                    deletionInProgress = false,
                    deletionResult = result
                )
            } else {
                activeBackup.copy(
                    error = result.errors.joinToString("; "),
                    progress = ResticBackupProgress(percent_done = 100.0),
                    deletionInProgress = false,
                    deletionResult = result
                )
            }

            activeBackup(folder.id).postValue(finalNotification)
            updateNotification(context, folder.id, finalNotification)

            // Update history entry with deletion result
            configure { config ->
                config.copy(folders = config.folders.map { currentFolder ->
                    if (currentFolder.id == folder.id) {
                        val lastEntry = currentFolder.history.firstOrNull()
                        if (lastEntry != null) {
                            val updatedEntry = lastEntry.copy(
                                deletionPerformed = result.success,
                                deletedFiles = result.deletedFiles,
                                deletedFolders = result.deletedFolders,
                                deletionErrors = if (result.errors.isNotEmpty()) result.errors.joinToString("; ") else null
                            )
                            currentFolder.copy(history = listOf(updatedEntry) + currentFolder.history.drop(1))
                        } else {
                            currentFolder
                        }
                    } else currentFolder
                })
            }

        } catch (e: Exception) {
            val errorMessage = "Unexpected error during folder deletion: ${e.message}"
            val errorNotification = activeBackup.copy(
                error = errorMessage,
                deletionInProgress = false
            )
            activeBackup(folder.id).postValue(errorNotification)
            updateNotification(context, folder.id, errorNotification)
        }
    }
}