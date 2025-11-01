package org.dydlakcloud.resticopia.restic

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import org.dydlakcloud.resticopia.util.getOrThrow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for ResticRepo restore functionality.
 * 
 * Tests the restoreAll method that enables downloading
 * entire snapshots without file filters.
 */
class ResticRepoRestoreTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var mockRestic: Restic
    private lateinit var testRepo: TestResticRepo

    @Before
    fun setUp() {
        mockRestic = mockk(relaxed = true)
        testRepo = TestResticRepo(mockRestic, "test-password")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `restoreAll calls restic with correct arguments`() {
        // Given: A snapshot ID, download path, and root path
        val snapshotId = ResticSnapshotId("abc123def456")
        val downloadPath = tempFolder.newFolder("download")
        val rootPath = File("/backup/path")

        val expectedArgs = listOf(
            "--json",
            "restore",
            "${snapshotId.id}:${rootPath.path}",
            "--target",
            downloadPath.path
        )

        // Mock the restic command to return success
        every { 
            mockRestic.restic(
                match { args ->
                    args.containsAll(expectedArgs)
                },
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns CompletableFuture.completedFuture(Pair(listOf("{\"message_type\":\"summary\"}"), emptyList()))

        // When: Calling restoreAll
        val result = testRepo.restoreAll(snapshotId, downloadPath, rootPath)
        result.getOrThrow(5.seconds)

        // Then: restic is called with correct restore arguments
        verify {
            mockRestic.restic(
                match { args ->
                    args.contains("--json") &&
                    args.contains("restore") &&
                    args.contains("${snapshotId.id}:${rootPath.path}") &&
                    args.contains("--target") &&
                    args.contains(downloadPath.path)
                },
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `restoreAll does not include file filters`() {
        // Given: Restore parameters
        val snapshotId = ResticSnapshotId("xyz789")
        val downloadPath = tempFolder.newFolder("download")
        val rootPath = File("/data")

        every { 
            mockRestic.restic(any(), any(), any(), any(), any(), any())
        } returns CompletableFuture.completedFuture(Pair(listOf("{}"), emptyList()))

        // When: Calling restoreAll
        testRepo.restoreAll(snapshotId, downloadPath, rootPath).getOrThrow()

        // Then: No --include or --exclude flags are present
        verify {
            mockRestic.restic(
                match { args ->
                    !args.contains("--include") && !args.contains("--exclude")
                },
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `restoreAll returns successful result`() {
        // Given: Mock successful restore
        val snapshotId = ResticSnapshotId("success123")
        val downloadPath = tempFolder.newFolder("download")
        val rootPath = File("/backup")

        val mockOutput = listOf(
            """{"message_type":"summary","files_restored":10,"total_bytes":1024}"""
        )

        every { 
            mockRestic.restic(any(), any(), any(), any(), any(), any())
        } returns CompletableFuture.completedFuture(Pair(mockOutput, emptyList()))

        // When: Calling restoreAll
        val result = testRepo.restoreAll(snapshotId, downloadPath, rootPath)
        val output = result.getOrThrow()

        // Then: Result contains output
        assertThat(output).isNotEmpty()
        assertThat(output).contains("summary")
    }

    @Test
    fun `restoreAll handles errors gracefully`() {
        // Given: Mock failed restore
        val snapshotId = ResticSnapshotId("error123")
        val downloadPath = tempFolder.newFolder("download")
        val rootPath = File("/backup")

        val testException = RuntimeException("Restore failed")
        every { 
            mockRestic.restic(any(), any(), any(), any(), any(), any())
        } returns CompletableFuture.failedFuture(testException)

        // When: Calling restoreAll
        val result = testRepo.restoreAll(snapshotId, downloadPath, rootPath)

        // Then: Exception is propagated
        try {
            result.getOrThrow()
            throw AssertionError("Expected exception to be thrown")
        } catch (e: Exception) {
            assertThat(e).isInstanceOf(RuntimeException::class.java)
            assertThat(e.message).contains("Restore failed")
        }
    }

    @Test
    fun `restoreAll uses correct snapshot path format`() {
        // Given: Various path formats
        val snapshotId = ResticSnapshotId("snap123")
        val downloadPath = tempFolder.newFolder("download")
        val rootPathAbsolute = File("/absolute/path")

        every { 
            mockRestic.restic(any(), any(), any(), any(), any(), any())
        } returns CompletableFuture.completedFuture(Pair(listOf("{}"), emptyList()))

        // When: Restoring with absolute path
        testRepo.restoreAll(snapshotId, downloadPath, rootPathAbsolute).getOrThrow()

        // Then: Snapshot path uses colon separator format
        verify {
            mockRestic.restic(
                match { args ->
                    args.any { it == "${snapshotId.id}:${rootPathAbsolute.path}" }
                },
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `restoreAll includes repository and password in environment`() {
        // Given: A configured repository
        val snapshotId = ResticSnapshotId("env123")
        val downloadPath = tempFolder.newFolder("download")
        val rootPath = File("/data")

        every { 
            mockRestic.restic(any(), any(), any(), any(), any(), any())
        } returns CompletableFuture.completedFuture(Pair(listOf("{}"), emptyList()))

        // When: Calling restoreAll
        testRepo.restoreAll(snapshotId, downloadPath, rootPath).getOrThrow()

        // Then: Environment variables include repository and password
        verify {
            mockRestic.restic(
                any(),
                match { vars ->
                    vars.any { it.first == "RESTIC_REPOSITORY" && it.second == "test-repo" } &&
                    vars.any { it.first == "RESTIC_PASSWORD" && it.second == "test-password" }
                },
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `restoreAll handles empty output`() {
        // Given: Restore that returns empty output
        val snapshotId = ResticSnapshotId("empty123")
        val downloadPath = tempFolder.newFolder("download")
        val rootPath = File("/data")

        every { 
            mockRestic.restic(any(), any(), any(), any(), any(), any())
        } returns CompletableFuture.completedFuture(Pair(emptyList(), emptyList()))

        // When: Calling restoreAll
        val result = testRepo.restoreAll(snapshotId, downloadPath, rootPath)
        val output = result.getOrThrow()

        // Then: Returns empty string
        assertThat(output).isEmpty()
    }

    @Test
    fun `restoreAll joins multiple output lines`() {
        // Given: Restore that returns multiple lines
        val snapshotId = ResticSnapshotId("multi123")
        val downloadPath = tempFolder.newFolder("download")
        val rootPath = File("/data")

        val mockOutput = listOf(
            """{"message_type":"status","percent_done":0.5}""",
            """{"message_type":"status","percent_done":1.0}""",
            """{"message_type":"summary","files_restored":5}"""
        )

        every { 
            mockRestic.restic(any(), any(), any(), any(), any(), any())
        } returns CompletableFuture.completedFuture(Pair(mockOutput, emptyList()))

        // When: Calling restoreAll
        val result = testRepo.restoreAll(snapshotId, downloadPath, rootPath)
        val output = result.getOrThrow()

        // Then: Output is joined with newlines
        assertThat(output).contains("percent_done")
        assertThat(output).contains("files_restored")
        assertThat(output.lines().size).isAtLeast(3)
    }

    @Test
    fun `restoreAll creates target directory if not exists`() {
        // Given: A non-existent download path (handled by restic)
        val snapshotId = ResticSnapshotId("mkdir123")
        val downloadPath = File(tempFolder.root, "nonexistent/nested/path")
        val rootPath = File("/data")

        every { 
            mockRestic.restic(any(), any(), any(), any(), any(), any())
        } returns CompletableFuture.completedFuture(Pair(listOf("{}"), emptyList()))

        // When: Calling restoreAll
        // restic should handle creating the target directory
        val result = testRepo.restoreAll(snapshotId, downloadPath, rootPath)

        // Then: Call completes without error
        result.getOrThrow()
        verify {
            mockRestic.restic(
                match { args -> args.contains(downloadPath.path) },
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `restore single file includes file filter`() {
        // Given: A single file restore (for comparison with restoreAll)
        val snapshotId = ResticSnapshotId("single123")
        val downloadPath = tempFolder.newFolder("download")
        val file = ResticFile(
            name = "test.txt",
            type = "file",
            path = File("/backup/path/test.txt"),
            mtime = java.time.ZonedDateTime.now(),
            atime = java.time.ZonedDateTime.now(),
            ctime = java.time.ZonedDateTime.now()
        )

        every { 
            mockRestic.restic(any(), any(), any(), any(), any(), any())
        } returns CompletableFuture.completedFuture(Pair(listOf("{}"), emptyList()))

        // When: Calling regular restore (not restoreAll)
        testRepo.restore(snapshotId, downloadPath, file).getOrThrow()

        // Then: Include filter is present for single file
        verify {
            mockRestic.restic(
                match { args ->
                    args.contains("--include") && args.contains(file.path.name)
                },
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    // Test implementation of ResticRepo for testing
    private class TestResticRepo(
        restic: Restic,
        password: String
    ) : ResticRepo(restic, password) {
        override fun repository(): String = "test-repo"
    }
}

