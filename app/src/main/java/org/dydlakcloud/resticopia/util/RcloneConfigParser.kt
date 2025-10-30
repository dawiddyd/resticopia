package org.dydlakcloud.resticopia.util

import java.io.File

/**
 * Parser for rclone configuration files.
 * Parses rclone.conf to extract remote names and their types.
 */
object RcloneConfigParser {
    
    data class RcloneRemote(
        val name: String,
        val type: String,
        val displayName: String = name
    ) {
        override fun toString(): String = "$name ($type)"
    }
    
    /**
     * Parse rclone.conf file and extract all configured remotes.
     * 
     * @param configFile The rclone.conf file to parse
     * @return List of RcloneRemote objects representing available remotes
     */
    fun parseConfig(configFile: File): List<RcloneRemote> {
        if (!configFile.exists() || !configFile.canRead()) {
            return emptyList()
        }
        
        val remotes = mutableListOf<RcloneRemote>()
        var currentRemoteName: String? = null
        var currentRemoteType: String? = null
        
        configFile.forEachLine { line ->
            val trimmed = line.trim()
            
            // Skip comments and empty lines
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                return@forEachLine
            }
            
            // Check for remote section header [remote_name]
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                // Save previous remote if exists
                val name = currentRemoteName
                val type = currentRemoteType
                if (name != null && type != null) {
                    remotes.add(RcloneRemote(name, type))
                }
                
                // Start new remote
                currentRemoteName = trimmed.substring(1, trimmed.length - 1)
                currentRemoteType = null
                return@forEachLine
            }
            
            // Check for type = value
            if (trimmed.startsWith("type", ignoreCase = true) && trimmed.contains("=")) {
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2) {
                    currentRemoteType = parts[1].trim()
                }
            }
        }
        
        // Add the last remote if exists
        val finalName = currentRemoteName
        val finalType = currentRemoteType
        if (finalName != null && finalType != null) {
            remotes.add(RcloneRemote(finalName, finalType))
        }
        
        return remotes
    }
    
    /**
     * Parse rclone.conf from string content.
     * 
     * @param content The content of rclone.conf as a string
     * @return List of RcloneRemote objects representing available remotes
     */
    fun parseConfigContent(content: String): List<RcloneRemote> {
        val remotes = mutableListOf<RcloneRemote>()
        var currentRemoteName: String? = null
        var currentRemoteType: String? = null
        
        content.lines().forEach { line ->
            val trimmed = line.trim()
            
            // Skip comments and empty lines
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                return@forEach
            }
            
            // Check for remote section header [remote_name]
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                // Save previous remote if exists
                val name = currentRemoteName
                val type = currentRemoteType
                if (name != null && type != null) {
                    remotes.add(RcloneRemote(name, type))
                }
                
                // Start new remote
                currentRemoteName = trimmed.substring(1, trimmed.length - 1)
                currentRemoteType = null
                return@forEach
            }
            
            // Check for type = value
            if (trimmed.startsWith("type", ignoreCase = true) && trimmed.contains("=")) {
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2) {
                    currentRemoteType = parts[1].trim()
                }
            }
        }
        
        // Add the last remote if exists
        val name = currentRemoteName
        val type = currentRemoteType
        if (name != null && type != null) {
            remotes.add(RcloneRemote(name, type))
        }
        
        return remotes
    }
    
    /**
     * Validate if a file appears to be a valid rclone config.
     * 
     * @param configFile The file to validate
     * @return true if file appears to be valid rclone config
     */
    fun isValidConfig(configFile: File): Boolean {
        if (!configFile.exists() || !configFile.canRead()) {
            return false
        }
        
        val remotes = parseConfig(configFile)
        return remotes.isNotEmpty()
    }
}

