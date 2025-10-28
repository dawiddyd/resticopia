package org.dydlakcloud.resticopia.restic

import java.io.File

/**
 * Local file system repository implementation for Restic.
 * This allows repositories to be stored on the local device filesystem,
 * which can then be synced to other locations using external tools.
 *
 * @param restic The Restic instance to use for executing commands
 * @param password The repository password for encryption
 * @param localPath The local filesystem path where the repository is located
 */
class ResticRepoLocal(
    restic: Restic,
    password: String,
    private val localPath: File,
) : ResticRepo(
    restic,
    password
) {
    /**
     * Returns the repository location in the format Restic expects for local repositories.
     * Uses the absolute path to the local directory.
     */
    override fun repository(): String = localPath.absolutePath

    /**
     * No remote hosts needed for local repositories.
     */
    override fun hosts(): List<String> = emptyList()
}

