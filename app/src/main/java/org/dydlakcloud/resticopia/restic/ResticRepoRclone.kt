package org.dydlakcloud.resticopia.restic

import android.content.Context
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
        println("DEBUG: Repository string: $repo")
        println("DEBUG: rcloneRemote: '$rcloneRemote', rclonePath: '$rclonePath'")
        return repo
    }

    override fun hosts(): List<String> = emptyList() // rclone doesn't require specific host entries
    
    override fun vars(): List<Pair<String, String>> {
        // Write global config to temp file at runtime
        val configFile = File(restic.storage.cache(), ".rclone.conf")
        try {
            configFile.writeText(rcloneConfig)
            // Debug logging
            println("DEBUG: Writing rclone config to ${configFile.absolutePath}")
            println("DEBUG: Config content length: ${rcloneConfig.length}")
            println("DEBUG: Config content preview: ${rcloneConfig.take(200)}")
            println("DEBUG: Config file exists: ${configFile.exists()}")
            println("DEBUG: Config file readable: ${configFile.canRead()}")
            if (configFile.exists()) {
                println("DEBUG: Config file size: ${configFile.length()}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Point rclone to the temp config file
        return super.vars().plus(Pair("RCLONE_CONFIG", configFile.absolutePath))
    }
}

