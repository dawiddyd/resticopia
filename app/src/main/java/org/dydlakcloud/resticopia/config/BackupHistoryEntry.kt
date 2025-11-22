package org.dydlakcloud.resticopia.config

import org.dydlakcloud.resticopia.DurationSerializer
import org.dydlakcloud.resticopia.ZonedDateTimeSerializer
import org.dydlakcloud.resticopia.restic.ResticSnapshotId
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.ZonedDateTime

@Serializable
data class BackupHistoryEntry(
    val timestamp: @Serializable(with = ZonedDateTimeSerializer::class) ZonedDateTime,
    val duration: @Serializable(with = DurationSerializer::class) Duration,
    val scheduled: Boolean,
    val cancelled: Boolean,
    val snapshotId: ResticSnapshotId?,
    val errorMessage: String?,
    val deletionPerformed: Boolean = false,
    val deletedFiles: Int = 0,
    val deletedFolders: Int = 0,
    val deletionErrors: String? = null
) {
    val successful get() = snapshotId != null
    val deletionSuccessful get() = deletionPerformed && deletionErrors == null
}