package org.dydlakcloud.resticopia.util

import android.content.Context
import android.util.Log
import org.dydlakcloud.resticopia.config.FolderConfig
import java.io.File
import java.io.IOException

/**
 * Manages safe deletion of folder contents after backup operations.
 * This class implements production-ready deletion logic with comprehensive error handling.
 *
 * IMPORTANT: Uses Application context to prevent memory leaks.
 */
class FolderDeletionManager(private val appContext: Context) {

    companion object {
        private const val TAG = "FolderDeletionManager"

        // Comprehensive list of dangerous paths that should never be deleted
        private val DANGEROUS_PATHS = setOf(
            "/system", "/sys", "/proc", "/dev",
            "/data/data", "/data/app", "/data/user", "/data/media",
            "/vendor", "/odm", "/oem",
            "/cache", "/metadata",
            "/mnt/asec", "/mnt/obb", "/mnt/secure",
            "/storage/emulated/0/Android/data", "/storage/emulated/0/Android/obb"
        )

        // Maximum directory depth to prevent infinite loops
        private const val MAX_DEPTH = 50
    }

    /**
     * Progress update during deletion
     */
    data class DeletionProgress(
        val filesProcessed: Int,
        val foldersProcessed: Int,
        val bytesFreed: Long,
        val totalFiles: Int = -1, // -1 if unknown
        val totalFolders: Int = -1, // -1 if unknown
        val currentPath: String? = null
    )

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
     * @param onProgress Optional callback for progress updates
     * @param cancelCheck Optional function to check if operation should be cancelled
     * @return DeletionResult with operation outcome
     */
    fun deleteFolderContents(
        folderConfig: FolderConfig,
        onProgress: ((DeletionProgress) -> Unit)? = null,
        cancelCheck: (() -> Boolean)? = null
    ): DeletionResult {
        Log.i(TAG, "Starting deletion of folder contents: ${folderConfig.path.absolutePath}")

        // Pre-deletion safety checks
        val safetyResult = performSafetyChecks(folderConfig.path)
        if (!safetyResult.success) {
            Log.e(TAG, "Safety checks failed: ${safetyResult.errors.joinToString()}")
            return DeletionResult(
                success = false,
                errors = safetyResult.errors
            )
        }

        return try {
            // First pass: count total items for progress reporting
            val totals = calculateTotals(folderConfig.path, 0)
            onProgress?.invoke(DeletionProgress(0, 0, 0, totals.files, totals.folders))

            // Second pass: perform actual deletion with progress reporting
            val result = deleteContentsRecursive(folderConfig.path, 0, totals, onProgress, cancelCheck)
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
     * Performs comprehensive safety checks before deletion
     */
    private fun performSafetyChecks(folder: File): SafetyCheckResult {
        val errors = mutableListOf<String>()

        // Check if path exists
        if (!folder.exists()) {
            errors.add("Folder does not exist: ${folder.absolutePath}")
            return SafetyCheckResult(false, errors)
        }

        // Check if it's actually a directory
        if (!folder.isDirectory) {
            errors.add("Path is not a directory: ${folder.absolutePath}")
            return SafetyCheckResult(false, errors)
        }

        // Check if we have write permissions
        if (!folder.canWrite()) {
            errors.add("No write permission for directory: ${folder.absolutePath}")
            return SafetyCheckResult(false, errors)
        }

        // Comprehensive path security validation
        val canonicalPath = try {
            folder.canonicalPath
        } catch (e: IOException) {
            errors.add("Cannot resolve canonical path: ${e.message}")
            return SafetyCheckResult(false, errors)
        }

        // Check against dangerous paths
        val dangerousPath = DANGEROUS_PATHS.any { dangerousPath ->
            canonicalPath.startsWith(dangerousPath) ||
            canonicalPath.contains("/$dangerousPath/")
        }

        if (dangerousPath) {
            errors.add("Refusing to delete from protected system path: $canonicalPath")
            return SafetyCheckResult(false, errors)
        }

        // Additional Android-specific checks
        if (canonicalPath.contains("/Android/data/") ||
            canonicalPath.contains("/Android/obb/")) {
            errors.add("Refusing to delete from Android app data directories: $canonicalPath")
            return SafetyCheckResult(false, errors)
        }

        // Check if directory is empty (though this shouldn't happen after backup)
        val contents = folder.listFiles()
        if (contents == null) {
            errors.add("Cannot list directory contents (permission denied or I/O error)")
            return SafetyCheckResult(false, errors)
        }

        if (contents.isEmpty()) {
            Log.w(TAG, "Directory is already empty: ${folder.absolutePath}")
        }

        return SafetyCheckResult(true, emptyList())
    }

    /**
     * Calculates total files and folders for progress reporting
     */
    private data class DeletionTotals(val files: Int, val folders: Int)

    private fun calculateTotals(folder: File, depth: Int): DeletionTotals {
        if (depth > MAX_DEPTH) return DeletionTotals(0, 0)

        var files = 0
        var folders = 0

        val contents = folder.listFiles() ?: return DeletionTotals(0, 0)

        for (file in contents) {
            if (file.isFile) {
                files++
            } else if (file.isDirectory) {
                folders++
                val subTotals = calculateTotals(file, depth + 1)
                files += subTotals.files
                folders += subTotals.folders
            }
        }

        return DeletionTotals(files, folders)
    }

    /**
     * Recursively deletes all contents of a directory with depth protection and progress reporting
     */
    private fun deleteContentsRecursive(
        folder: File,
        depth: Int,
        totals: DeletionTotals,
        onProgress: ((DeletionProgress) -> Unit)?,
        cancelCheck: (() -> Boolean)?
    ): DeletionResult {
        // Prevent infinite loops from circular symlinks or extremely deep directory structures
        if (depth > MAX_DEPTH) {
            return DeletionResult(
                success = false,
                errors = listOf("Maximum directory depth exceeded (possible circular symlink)"),
                partialFailure = true
            )
        }

        var deletedFiles = 0
        var deletedFolders = 0
        var totalBytesFreed = 0L
        val errors = mutableListOf<String>()
        var partialFailure = false

        val contents = folder.listFiles()
        if (contents == null) {
            val errorMsg = "Cannot list directory contents: ${folder.absolutePath}"
            Log.e(TAG, errorMsg)
            return DeletionResult(false, 0, 0, 0L, listOf(errorMsg), true)
        }

        // Process files first, then directories (reverse order for safe deletion)
        val sortedContents = contents.sortedBy { it.isDirectory }

        for (file in sortedContents) {
            // Check for cancellation
            if (cancelCheck?.invoke() == true) {
                Log.i(TAG, "Deletion cancelled by user")
                return DeletionResult(
                    success = false,
                    deletedFiles = deletedFiles,
                    deletedFolders = deletedFolders,
                    totalBytesFreed = totalBytesFreed,
                    errors = listOf("Operation cancelled by user"),
                    partialFailure = true
                )
            }

            try {
                if (file.isFile) {
                    val size = file.length()
                    if (file.delete()) {
                        deletedFiles++
                        totalBytesFreed += size
                        Log.d(TAG, "Deleted file: ${file.absolutePath} (${size} bytes)")

                        // Report progress every few files to avoid too frequent updates
                        if (deletedFiles % 10 == 0 || deletedFiles == totals.files) {
                            onProgress?.invoke(DeletionProgress(
                                filesProcessed = deletedFiles,
                                foldersProcessed = deletedFolders,
                                bytesFreed = totalBytesFreed,
                                totalFiles = totals.files,
                                totalFolders = totals.folders,
                                currentPath = file.absolutePath
                            ))
                        }
                    } else {
                        val errorMsg = "Failed to delete file: ${file.absolutePath}"
                        Log.e(TAG, errorMsg)
                        errors.add(errorMsg)
                        partialFailure = true
                    }
                } else if (file.isDirectory) {
                    // Recursively delete subdirectory contents first
                    val subResult = deleteContentsRecursive(file, depth + 1, totals, onProgress, cancelCheck)
                    deletedFiles += subResult.deletedFiles
                    deletedFolders += subResult.deletedFolders
                    totalBytesFreed += subResult.totalBytesFreed

                    if (subResult.partialFailure) {
                        partialFailure = true
                        errors.addAll(subResult.errors.map { "Subdirectory ${file.name}: $it" })
                    }

                    // Now delete the empty directory
                    if (file.delete()) {
                        deletedFolders++
                        Log.d(TAG, "Deleted directory: ${file.absolutePath}")

                        // Report progress after directory deletion
                        onProgress?.invoke(DeletionProgress(
                            filesProcessed = deletedFiles,
                            foldersProcessed = deletedFolders,
                            bytesFreed = totalBytesFreed,
                            totalFiles = totals.files,
                            totalFolders = totals.folders,
                            currentPath = file.absolutePath
                        ))
                    } else {
                        val errorMsg = "Failed to delete directory: ${file.absolutePath}"
                        Log.e(TAG, errorMsg)
                        errors.add(errorMsg)
                        partialFailure = true
                    }
                }
            } catch (e: SecurityException) {
                val errorMsg = "Permission denied deleting ${file.name}: ${e.message}"
                Log.e(TAG, errorMsg, e)
                errors.add(errorMsg)
                partialFailure = true
            } catch (e: Exception) {
                val errorMsg = "Unexpected error deleting ${file.name}: ${e.message}"
                Log.e(TAG, errorMsg, e)
                errors.add(errorMsg)
                partialFailure = true
            }
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
