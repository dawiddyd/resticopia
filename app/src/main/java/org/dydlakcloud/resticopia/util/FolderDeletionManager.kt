package org.dydlakcloud.resticopia.util

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.Log
import org.dydlakcloud.resticopia.config.FolderConfig
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * Manages safe deletion of folder contents after backup operations.
 * This class implements production-ready deletion logic with comprehensive error handling.
 */
class FolderDeletionManager(private val context: Context) {

    companion object {
        private const val TAG = "FolderDeletionManager"
    }

    /**
     * Result of a folder deletion operation
     */
    data class DeletionResult(
        val success: Boolean,
        val deletedFiles: Int = 0,
        val deletedFolders: Int = 0,
        val totalBytesFreed: Long = 0L,
        val errors: List<String> = emptyList(),
        val partialFailure: Boolean = false
    )

    /**
     * Deletes all contents of a folder after successful backup.
     * This is a destructive operation that permanently removes all files and subdirectories.
     *
     * @param folderConfig The folder configuration
     * @return DeletionResult with operation outcome
     */
    fun deleteFolderContents(folderConfig: FolderConfig): DeletionResult {
        Log.i(TAG, "Starting deletion of folder contents: ${folderConfig.path.absolutePath}")

        val folderPath = folderConfig.path.toPath()

        // Pre-deletion safety checks
        val safetyResult = performSafetyChecks(folderPath)
        if (!safetyResult.success) {
            Log.e(TAG, "Safety checks failed: ${safetyResult.errors.joinToString()}")
            return DeletionResult(
                success = false,
                errors = safetyResult.errors
            )
        }

        return try {
            val result = deleteContentsRecursive(folderPath)
            Log.i(TAG, "Deletion completed: ${result.deletedFiles} files, ${result.deletedFolders} folders, ${result.totalBytesFreed} bytes freed")

            if (result.partialFailure) {
                Log.w(TAG, "Partial deletion failure: ${result.errors.joinToString()}")
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during deletion", e)
            DeletionResult(
                success = false,
                errors = listOf("Critical error: ${e.message}")
            )
        }
    }

    /**
     * Performs safety checks before deletion
     */
    private fun performSafetyChecks(folderPath: Path): SafetyCheckResult {
        val errors = mutableListOf<String>()

        // Check if path exists
        if (!Files.exists(folderPath)) {
            errors.add("Folder does not exist: $folderPath")
            return SafetyCheckResult(false, errors)
        }

        // Check if it's actually a directory
        if (!Files.isDirectory(folderPath)) {
            errors.add("Path is not a directory: $folderPath")
            return SafetyCheckResult(false, errors)
        }

        // Check if we have write permissions
        if (!Files.isWritable(folderPath)) {
            errors.add("No write permission for directory: $folderPath")
            return SafetyCheckResult(false, errors)
        }

        // Check for dangerous system paths (basic protection)
        val pathString = folderPath.toString()
        val dangerousPaths = listOf(
            "/system", "/sys", "/proc", "/dev", "/data/data",
            "/data/app", "/data/user", "/data/media"
        )

        if (dangerousPaths.any { pathString.startsWith(it) }) {
            errors.add("Refusing to delete from system path: $pathString")
            return SafetyCheckResult(false, errors)
        }

        // Check if directory is empty (though this shouldn't happen after backup)
        val contents = try {
            Files.list(folderPath).use { stream -> stream.collect(java.util.stream.Collectors.toList()) }
        } catch (e: IOException) {
            errors.add("Cannot list directory contents: ${e.message}")
            return SafetyCheckResult(false, errors)
        }

        if (contents.isEmpty()) {
            Log.w(TAG, "Directory is already empty: $folderPath")
        }

        return SafetyCheckResult(true, emptyList())
    }

    /**
     * Recursively deletes all contents of a directory
     */
    private fun deleteContentsRecursive(folderPath: Path): DeletionResult {
        var deletedFiles = 0
        var deletedFolders = 0
        var totalBytesFreed = 0L
        val errors = mutableListOf<String>()
        var partialFailure = false

        try {
            // Get all contents first (to avoid ConcurrentModificationException)
            val contents = Files.list(folderPath).use { stream -> stream.collect(java.util.stream.Collectors.toList()) }

            // Delete files first, then directories
            for (path in contents) {
                try {
                    if (Files.isRegularFile(path)) {
                        val size = Files.size(path)
                        Files.delete(path)
                        deletedFiles++
                        totalBytesFreed += size
                        Log.d(TAG, "Deleted file: $path (${size} bytes)")
                    } else if (Files.isDirectory(path)) {
                        // Recursively delete subdirectory contents first
                        val subResult = deleteContentsRecursive(path)
                        deletedFiles += subResult.deletedFiles
                        deletedFolders += subResult.deletedFolders
                        totalBytesFreed += subResult.totalBytesFreed

                        if (subResult.partialFailure) {
                            partialFailure = true
                            errors.addAll(subResult.errors)
                        }

                        // Now delete the empty directory
                        Files.delete(path)
                        deletedFolders++
                        Log.d(TAG, "Deleted directory: $path")
                    }
                } catch (e: IOException) {
                    val errorMsg = "Failed to delete ${path.fileName}: ${e.message}"
                    Log.e(TAG, errorMsg, e)
                    errors.add(errorMsg)
                    partialFailure = true
                } catch (e: SecurityException) {
                    val errorMsg = "Permission denied deleting ${path.fileName}: ${e.message}"
                    Log.e(TAG, errorMsg, e)
                    errors.add(errorMsg)
                    partialFailure = true
                }
            }
        } catch (e: IOException) {
            val errorMsg = "Failed to list directory contents: ${e.message}"
            Log.e(TAG, errorMsg, e)
            errors.add(errorMsg)
            return DeletionResult(false, 0, 0, 0L, errors, true)
        }

        return DeletionResult(
            success = !partialFailure || (deletedFiles > 0 || deletedFolders > 0),
            deletedFiles = deletedFiles,
            deletedFolders = deletedFolders,
            totalBytesFreed = totalBytesFreed,
            errors = errors,
            partialFailure = partialFailure
        )
    }

    /**
     * Safety check result
     */
    private data class SafetyCheckResult(
        val success: Boolean,
        val errors: List<String>
    )
}
