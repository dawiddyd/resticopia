package org.dydlakcloud.resticopia.restic

import timber.log.Timber
import java.io.File

class ResticRepoRclone(
    restic: Restic,
    password: String,
    private val rcloneRemote: String,
    private val rclonePath: String,
    private val rcloneConfig: String // Global config content from Config
) : ResticRepo(
    restic,
    password
) {
    override fun repository(): String {
        val repo = "rclone:$rcloneRemote:$rclonePath"
        Timber.d("Repository string: $repo")
        Timber.d("rcloneRemote: '$rcloneRemote', rclonePath: '$rclonePath'")
        return repo
    }

    override fun hosts(): List<String> = emptyList() // rclone doesn't require specific host entries
    
    override fun vars(): List<Pair<String, String>> {
        // Write global config to temp file at runtime
        val configFile = File(restic.storage.cache(), ".rclone.conf")
        try {
            configFile.writeText(rcloneConfig)
            // Debug logging
            Timber.d("Writing rclone config to ${configFile.absolutePath}")
            Timber.d("Config content length: ${rcloneConfig.length}")
            Timber.d("Config content preview: ${rcloneConfig.take(200)}")
            Timber.d("Config file exists: ${configFile.exists()}")
            Timber.d("Config file readable: ${configFile.canRead()}")
            if (configFile.exists()) {
                Timber.d("Config file size: ${configFile.length()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error writing rclone config to file: ${e.message}")
        }

        // Point rclone to the temp config file
        return super.vars().plus(Pair("RCLONE_CONFIG", configFile.absolutePath))
    }
}

