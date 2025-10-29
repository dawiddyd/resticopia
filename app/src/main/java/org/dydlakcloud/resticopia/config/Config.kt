package org.dydlakcloud.resticopia.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Config(
    val repos: List<RepoConfig>,
    val folders: List<FolderConfig>,
    val hostname: String?,
    val nameServers: List<String>?,
    val ntfyUrl: String? = null,
    val rcloneConfig: String? = null // Global rclone configuration content
) {
    companion object {
        val format = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        fun fromJsonString(json: String): Config = format.decodeFromString(serializer(), json)
    }

    fun toJsonString(): String = format.encodeToString(serializer(), this)
}