package org.dydlakcloud.resticopia.restic

class ResticRepoRclone(
    restic: Restic,
    password: String,
    private val rcloneRemote: String,
    private val rclonePath: String
) : ResticRepo(
    restic,
    password
) {
    override fun repository(): String = "rclone:$rcloneRemote:$rclonePath"

    override fun hosts(): List<String> = emptyList() // rclone doesn't require specific host entries
}

