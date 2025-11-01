package org.dydlakcloud.resticopia.fixtures

import org.dydlakcloud.resticopia.config.*
import org.dydlakcloud.resticopia.restic.ResticFile
import org.dydlakcloud.resticopia.restic.ResticSnapshot
import org.dydlakcloud.resticopia.restic.ResticSnapshotId
import java.io.File
import java.net.URI
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

/**
 * Test fixtures for creating test data.
 * Following the Test Data Builder pattern for maintainable test data.
 */
object TestFixtures {
    
    // Repository Fixtures
    
    fun createS3RepoConfig(
        id: UUID = UUID.randomUUID(),
        name: String = "Test S3 Repo",
        password: String = "test-password",
        s3Url: String = "https://s3.amazonaws.com/my-bucket",
        accessKeyId: String = "AKIAIOSFODNN7EXAMPLE",
        secretAccessKey: String = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
        s3DefaultRegion: String = "us-east-1"
    ): RepoConfig {
        return RepoConfig(
            base = RepoBaseConfig(
                id = RepoConfigId(id),
                name = name,
                type = RepoType.S3,
                password = Secret(password)
            ),
            params = S3RepoParams(
                s3Url = URI(s3Url),
                accessKeyId = accessKeyId,
                secretAccessKey = Secret(secretAccessKey),
                s3DefaultRegion = s3DefaultRegion
            )
        )
    }
    
    fun createRestRepoConfig(
        id: UUID = UUID.randomUUID(),
        name: String = "Test REST Repo",
        password: String = "test-password",
        restUrl: String = "http://localhost:8000/"
    ): RepoConfig {
        return RepoConfig(
            base = RepoBaseConfig(
                id = RepoConfigId(id),
                name = name,
                type = RepoType.Rest,
                password = Secret(password)
            ),
            params = RestRepoParams(
                restUrl = URI(restUrl)
            )
        )
    }
    
    fun createLocalRepoConfig(
        id: UUID = UUID.randomUUID(),
        name: String = "Test Local Repo",
        password: String = "test-password",
        localPath: String = "/storage/emulated/0/restic-repo"
    ): RepoConfig {
        return RepoConfig(
            base = RepoBaseConfig(
                id = RepoConfigId(id),
                name = name,
                type = RepoType.Local,
                password = Secret(password)
            ),
            params = LocalRepoParams(
                localPath = localPath
            )
        )
    }
    
    fun createRcloneRepoConfig(
        id: UUID = UUID.randomUUID(),
        name: String = "Test Rclone Repo",
        password: String = "test-password",
        rcloneRemote: String = "webdav-test",
        rclonePath: String = "backups/restic"
    ): RepoConfig {
        return RepoConfig(
            base = RepoBaseConfig(
                id = RepoConfigId(id),
                name = name,
                type = RepoType.Rclone,
                password = Secret(password)
            ),
            params = RcloneRepoParams(
                rcloneRemote = rcloneRemote,
                rclonePath = rclonePath
            )
        )
    }
    
    // Folder Fixtures
    
    fun createFolderConfig(
        id: UUID = UUID.randomUUID(),
        repoId: UUID = UUID.randomUUID(),
        path: String = "/storage/emulated/0/Documents",
        schedule: String = "0 2 * * *",
        keepLast: Int? = 7,
        keepWithin: Duration? = Duration.ofDays(30),
        history: List<BackupHistoryEntry> = emptyList()
    ): FolderConfig {
        return FolderConfig(
            id = FolderConfigId(id),
            repoId = RepoConfigId(repoId),
            path = java.io.File(path),
            schedule = schedule,
            keepLast = keepLast,
            keepWithin = keepWithin,
            history = history
        )
    }
    
    fun createBackupHistoryEntry(
        timestamp: ZonedDateTime = ZonedDateTime.now(),
        duration: Duration = Duration.ofMinutes(1),
        scheduled: Boolean = true,
        cancelled: Boolean = false,
        snapshotId: ResticSnapshotId? = ResticSnapshotId("abc123def456"),
        errorMessage: String? = null
    ): BackupHistoryEntry {
        return BackupHistoryEntry(
            timestamp = timestamp,
            duration = duration,
            scheduled = scheduled,
            cancelled = cancelled,
            snapshotId = snapshotId,
            errorMessage = errorMessage
        )
    }
    
    // Config Fixtures
    
    fun createConfig(
        repos: List<RepoConfig> = listOf(createS3RepoConfig()),
        folders: List<FolderConfig> = listOf(createFolderConfig()),
        hostname: String? = "test-device",
        nameServers: List<String>? = listOf("8.8.8.8", "8.8.4.4"),
        ntfyUrl: String? = null,
        rcloneConfig: String? = null
    ): Config {
        return Config(
            repos = repos,
            folders = folders,
            hostname = hostname,
            nameServers = nameServers,
            ntfyUrl = ntfyUrl,
            rcloneConfig = rcloneConfig
        )
    }
    
    // Rclone Config Fixture
    
    fun createRcloneConfig(
        remoteName: String = "webdav-test",
        remoteType: String = "webdav",
        additionalParams: Map<String, String> = mapOf(
            "url" to "http://localhost:8080/dav",
            "vendor" to "other"
        )
    ): String {
        val config = StringBuilder()
        config.appendLine("[$remoteName]")
        config.appendLine("type = $remoteType")
        additionalParams.forEach { (key, value) ->
            config.appendLine("$key = $value")
        }
        config.appendLine()
        return config.toString()
    }
    
    // Snapshot Fixtures
    
    fun createResticSnapshot(
        id: String = "abc123def456789",
        time: ZonedDateTime = ZonedDateTime.now(),
        hostname: String = "test-device",
        paths: List<File> = listOf(File("/storage/emulated/0/Documents")),
        tree: String = "tree123abc456def",
        parent: ResticSnapshotId? = null
    ): ResticSnapshot {
        return ResticSnapshot(
            id = ResticSnapshotId(id),
            time = time,
            hostname = hostname,
            paths = paths,
            tree = tree,
            parent = parent
        )
    }
    
    fun createResticFile(
        name: String = "test-file.txt",
        type: String = "file",
        path: File = File("/backup/path/test-file.txt"),
        mtime: ZonedDateTime = ZonedDateTime.now(),
        atime: ZonedDateTime = ZonedDateTime.now(),
        ctime: ZonedDateTime = ZonedDateTime.now()
    ): ResticFile {
        return ResticFile(
            name = name,
            type = type,
            path = path,
            mtime = mtime,
            atime = atime,
            ctime = ctime
        )
    }
    
    fun createResticDirectory(
        name: String = "test-directory",
        path: File = File("/backup/path/test-directory"),
        mtime: ZonedDateTime = ZonedDateTime.now(),
        atime: ZonedDateTime = ZonedDateTime.now(),
        ctime: ZonedDateTime = ZonedDateTime.now()
    ): ResticFile {
        return ResticFile(
            name = name,
            type = "dir",
            path = path,
            mtime = mtime,
            atime = atime,
            ctime = ctime
        )
    }
    
    fun createResticSnapshotId(
        id: String = "abc123def456"
    ): ResticSnapshotId {
        return ResticSnapshotId(id)
    }
}

