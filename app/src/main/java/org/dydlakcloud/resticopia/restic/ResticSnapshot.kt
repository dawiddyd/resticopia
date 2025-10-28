package org.dydlakcloud.resticopia.restic

import org.dydlakcloud.resticopia.FileSerializer
import org.dydlakcloud.resticopia.ZonedDateTimeSerializer
import kotlinx.serialization.Serializable
import java.io.File
import java.time.ZonedDateTime

@Serializable
data class ResticSnapshot(
    val time: @Serializable(with = ZonedDateTimeSerializer::class) ZonedDateTime,
    val parent: ResticSnapshotId? = null,
    val tree: String,
    val paths: List<@Serializable(with = FileSerializer::class) File>,
    val hostname: String,
    val id: ResticSnapshotId
)