package org.dydlakcloud.resticopia.restic

import org.dydlakcloud.resticopia.FileSerializer
import org.dydlakcloud.resticopia.ZonedDateTimeSerializer
import kotlinx.serialization.Serializable
import org.dydlakcloud.resticopia.util.FileUtil
import java.io.File
import java.time.ZonedDateTime

@Serializable
data class ResticFile(
    val name: String,
    val type: String,
    val size: Long = 0L,
    val path: @Serializable(with = FileSerializer::class) File,
    val mtime: @Serializable(with = ZonedDateTimeSerializer::class) ZonedDateTime,
    val atime: @Serializable(with = ZonedDateTimeSerializer::class) ZonedDateTime,
    val ctime: @Serializable(with = ZonedDateTimeSerializer::class) ZonedDateTime
) {

    val humanReadableSize: String by lazy { FileUtil.formatFileSize(size) }

}