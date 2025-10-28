package de.lolhens.resticui.config

import android.util.Base64
import de.lolhens.resticui.DurationSerializer
import de.lolhens.resticui.FileSerializer
import de.lolhens.resticui.URISerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.time.Duration
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable configuration format that can be transferred between devices.
 * Secrets are stored as plain text (or optionally encrypted with user password).
 */
@Serializable
data class PortableConfig(
    val version: Int = 2, // Version 2 = portable format
    val repos: List<PortableRepoConfig>,
    val folders: List<PortableFolderConfig>,
    val hostname: String?,
    val nameServers: List<String>?,
    val encrypted: Boolean = false,
    val passwordHash: String? = null // SHA-256 hash to verify password
) {
    companion object {
        private const val PBKDF2_ITERATIONS = 10000
        private const val KEY_LENGTH = 256
        
        val format = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        /**
         * Convert device-specific Config to PortableConfig
         * Password is required for security
         */
        fun fromConfig(config: Config, exportPassword: String): PortableConfig {
            require(exportPassword.isNotEmpty()) { "Export password is required" }
            val portableRepos = config.repos.map { repo ->
                PortableRepoConfig(
                    id = repo.base.id.toString(),
                    name = repo.base.name,
                    type = repo.base.type.name,
                    password = repo.base.password.secret,
                    params = when (repo.params) {
                        is S3RepoParams -> PortableRepoParams.S3(
                            s3Url = repo.params.s3Url.toString(),
                            accessKeyId = repo.params.accessKeyId,
                            secretAccessKey = repo.params.secretAccessKey.secret,
                            s3DefaultRegion = repo.params.s3DefaultRegion
                        )
                        is RestRepoParams -> PortableRepoParams.Rest(
                            restUrl = repo.params.restUrl.toString()
                        )
                        is B2RepoParams -> PortableRepoParams.B2(
                            b2Url = repo.params.b2Url.toString(),
                            b2AccountId = repo.params.b2AccountId,
                            b2AccountKey = repo.params.b2AccountKey.secret
                        )
                        else -> throw IllegalArgumentException("Unknown repo type")
                    }
                )
            }

            val portableFolders = config.folders.map { folder ->
                PortableFolderConfig(
                    id = folder.id.toString(),
                    repoId = folder.repoId.toString(),
                    path = folder.path.absolutePath,
                    schedule = folder.schedule,
                    keepLast = folder.keepLast,
                    keepWithinHours = folder.keepWithin?.toHours(),
                    history = folder.history
                )
            }

            val portableConfig = PortableConfig(
                repos = portableRepos,
                folders = portableFolders,
                hostname = config.hostname,
                nameServers = config.nameServers,
                encrypted = false,
                passwordHash = null
            )

            // Always encrypt with password
            return portableConfig.encrypt(exportPassword)
        }

        /**
         * Load PortableConfig from JSON string
         */
        fun fromJsonString(json: String): PortableConfig {
            return format.decodeFromString(serializer(), json)
        }
    }

    /**
     * Convert PortableConfig to device-specific Config
     */
    fun toConfig(): Config {
        val repos = this.repos.map { portableRepo ->
            val repoType = RepoType.valueOf(portableRepo.type)
            val params = when (portableRepo.params) {
                is PortableRepoParams.S3 -> S3RepoParams(
                    s3Url = URI(portableRepo.params.s3Url),
                    accessKeyId = portableRepo.params.accessKeyId,
                    secretAccessKey = Secret(portableRepo.params.secretAccessKey),
                    s3DefaultRegion = portableRepo.params.s3DefaultRegion
                )
                is PortableRepoParams.Rest -> RestRepoParams(
                    restUrl = URI(portableRepo.params.restUrl)
                )
                is PortableRepoParams.B2 -> B2RepoParams(
                    b2Url = URI(portableRepo.params.b2Url),
                    b2AccountId = portableRepo.params.b2AccountId,
                    b2AccountKey = Secret(portableRepo.params.b2AccountKey)
                )
            }

            val baseConfig = RepoBaseConfig(
                id = RepoConfigId.fromString(portableRepo.id),
                name = portableRepo.name,
                type = repoType,
                password = Secret(portableRepo.password)
            )

            RepoConfig(baseConfig, params)
        }

        val folders = this.folders.map { portableFolder ->
            FolderConfig(
                id = FolderConfigId.fromString(portableFolder.id),
                repoId = RepoConfigId.fromString(portableFolder.repoId),
                path = File(portableFolder.path),
                schedule = portableFolder.schedule,
                keepLast = portableFolder.keepLast,
                keepWithin = portableFolder.keepWithinHours?.let { Duration.ofHours(it) },
                history = portableFolder.history
            )
        }

        return Config(
            repos = repos,
            folders = folders,
            hostname = hostname,
            nameServers = nameServers
        )
    }

    fun toJsonString(): String = format.encodeToString(serializer(), this)

    /**
     * Encrypt this config with a user password
     */
    fun encrypt(password: String): PortableConfig {
        val json = this.copy(encrypted = false, passwordHash = null).toJsonString()
        val encrypted = encryptString(json, password)
        val hash = hashPassword(password)
        
        // Return a minimal config with encrypted data embedded
        return PortableConfig(
            version = 2,
            repos = listOf(
                PortableRepoConfig(
                    id = "encrypted",
                    name = "ENCRYPTED_DATA",
                    type = "ENCRYPTED",
                    password = encrypted,
                    params = PortableRepoParams.Rest("")
                )
            ),
            folders = emptyList(),
            hostname = null,
            nameServers = null,
            encrypted = true,
            passwordHash = hash
        )
    }

    /**
     * Decrypt this config with a user password
     */
    fun decrypt(password: String): PortableConfig {
        if (!encrypted) return this
        
        // Verify password hash
        if (passwordHash != null && hashPassword(password) != passwordHash) {
            throw IllegalArgumentException("Invalid password")
        }

        val encryptedData = repos.firstOrNull()?.password
            ?: throw IllegalArgumentException("No encrypted data found")
        
        val json = decryptString(encryptedData, password)
        return fromJsonString(json)
    }

    private fun encryptString(plaintext: String, password: String): String {
        val salt = ByteArray(16).apply { java.security.SecureRandom().nextBytes(this) }
        val iv = ByteArray(16).apply { java.security.SecureRandom().nextBytes(this) }
        
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = salt + iv + encrypted
        
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptString(ciphertext: String, password: String): String {
        val combined = Base64.decode(ciphertext, Base64.NO_WRAP)
        
        val salt = combined.sliceArray(0 until 16)
        val iv = combined.sliceArray(16 until 32)
        val encrypted = combined.sliceArray(32 until combined.size)
        
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        return factory.generateSecret(spec).encoded
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}

@Serializable
data class PortableRepoConfig(
    val id: String,
    val name: String,
    val type: String,
    val password: String, // Plain text password
    val params: PortableRepoParams
)

@Serializable
sealed class PortableRepoParams {
    @Serializable
    data class S3(
        val s3Url: String,
        val accessKeyId: String,
        val secretAccessKey: String, // Plain text
        val s3DefaultRegion: String
    ) : PortableRepoParams()

    @Serializable
    data class Rest(
        val restUrl: String
    ) : PortableRepoParams()

    @Serializable
    data class B2(
        val b2Url: String,
        val b2AccountId: String,
        val b2AccountKey: String // Plain text
    ) : PortableRepoParams()
}

@Serializable
data class PortableFolderConfig(
    val id: String,
    val repoId: String,
    val path: String,
    val schedule: String,
    val keepLast: Int? = null,
    val keepWithinHours: Long? = null,
    val history: List<BackupHistoryEntry> = emptyList()
)

