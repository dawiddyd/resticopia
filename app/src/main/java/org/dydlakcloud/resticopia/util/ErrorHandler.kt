package org.dydlakcloud.resticopia.util

import android.content.Context
import org.dydlakcloud.resticopia.R
import org.dydlakcloud.resticopia.restic.ResticException

class ErrorHandler(private val context: Context) {

    enum class ErrorCategory {
        AUTHENTICATION,
        CONFIGURATION,
        NETWORK,
        PERMISSION,
        REPOSITORY_NOT_FOUND,
        REPOSITORY_CORRUPTED,
        STORAGE_FULL,
        CONNECTION_TIMEOUT,
        INVALID_CREDENTIALS,
        RCLONE_CONFIG,
        RCLONE_REMOTE_NOT_FOUND,
        UNKNOWN
    }

    data class UserFriendlyError(
        val category: ErrorCategory,
        val title: String,
        val message: String,
        val suggestion: String? = null,
        val originalError: String
    )

    fun getUserFriendlyError(throwable: Throwable): UserFriendlyError {
        val originalMessage = when (throwable) {
            is ResticException -> throwable.message ?: "Unknown error"
            else -> throwable.message ?: "Unknown error"
        }

        // Check for specific error patterns and return user-friendly messages
        return when {
            // Rclone configuration errors
            isRcloneConfigError(originalMessage) -> createRcloneConfigError(originalMessage)
            isRcloneRemoteNotFoundError(originalMessage) -> createRcloneRemoteNotFoundError(extractRemoteNameFromError(originalMessage), originalMessage)
            isRcloneSectionNotFoundError(originalMessage) -> createRcloneSectionNotFoundError(extractRemoteNameFromError(originalMessage), originalMessage)

            // Authentication errors
            isAuthenticationError(originalMessage) -> createAuthenticationError(originalMessage)
            isInvalidPasswordError(originalMessage) -> createInvalidPasswordError(originalMessage)

            // Repository errors
            isRepositoryNotFoundError(originalMessage) -> createRepositoryNotFoundError(originalMessage)
            isRepositoryCorruptedError(originalMessage) -> createRepositoryCorruptedError(originalMessage)

            // Network errors
            isNetworkError(originalMessage) -> createNetworkError(originalMessage)
            isTimeoutError(originalMessage) -> createTimeoutError(originalMessage)

            // Storage errors
            isStorageFullError(originalMessage) -> createStorageFullError(originalMessage)

            // Permission errors
            isPermissionError(originalMessage) -> createPermissionError(originalMessage)

            // Default fallback
            else -> createGenericError(originalMessage)
        }
    }

    private fun isRcloneConfigError(message: String): Boolean {
        return message.contains("didn't find section in config file") ||
               message.contains("failed to find generated linker configuration") ||
               message.contains("rclone: WARNING: linker:") ||
               message.contains("rclone: CRITICAL:")
    }

    private fun isRcloneRemoteNotFoundError(message: String): Boolean {
        return message.contains("didn't find section in config file")
    }

    private fun isRcloneSectionNotFoundError(message: String): Boolean {
        return message.contains("didn't find section in config file")
    }

    private fun isAuthenticationError(message: String): Boolean {
        return message.contains("authentication failed") ||
               message.contains("unauthorized") ||
               message.contains("access denied")
    }

    private fun isInvalidPasswordError(message: String): Boolean {
        return message.contains("wrong password") ||
               message.contains("invalid password") ||
               message.contains("password verification failed")
    }

    private fun isRepositoryNotFoundError(message: String): Boolean {
        return message.contains("repository does not exist") ||
               message.contains("repository not found") ||
               message.contains("no such file or directory")
    }

    private fun isRepositoryCorruptedError(message: String): Boolean {
        return message.contains("repository is corrupted") ||
               message.contains("repository integrity check failed") ||
               message.contains("repository format version")
    }

    private fun isNetworkError(message: String): Boolean {
        return message.contains("network is unreachable") ||
               message.contains("connection refused") ||
               message.contains("connection reset") ||
               message.contains("no route to host")
    }

    private fun isTimeoutError(message: String): Boolean {
        return message.contains("timeout") ||
               message.contains("connection timed out")
    }

    private fun isStorageFullError(message: String): Boolean {
        return message.contains("no space left on device") ||
               message.contains("disk full") ||
               message.contains("insufficient storage")
    }

    private fun isPermissionError(message: String): Boolean {
        return message.contains("permission denied") ||
               message.contains("access denied") ||
               message.contains("operation not permitted")
    }

    private fun createRcloneConfigError(originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.RCLONE_CONFIG,
            title = context.getString(R.string.error_rclone_config_title),
            message = context.getString(R.string.error_rclone_config_message),
            suggestion = context.getString(R.string.error_rclone_config_suggestion),
            originalError = originalMessage
        )
    }

    private fun createRcloneRemoteNotFoundError(remoteName: String, originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.RCLONE_REMOTE_NOT_FOUND,
            title = context.getString(R.string.error_rclone_remote_not_found_title),
            message = context.getString(R.string.error_rclone_remote_not_found_message, remoteName),
            suggestion = context.getString(R.string.error_rclone_remote_not_found_suggestion),
            originalError = originalMessage
        )
    }

    private fun createRcloneSectionNotFoundError(remoteName: String, originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.RCLONE_REMOTE_NOT_FOUND,
            title = context.getString(R.string.error_rclone_section_not_found_title),
            message = context.getString(R.string.error_rclone_section_not_found_message, remoteName),
            suggestion = context.getString(R.string.error_rclone_section_not_found_suggestion),
            originalError = originalMessage
        )
    }

    private fun createAuthenticationError(originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.AUTHENTICATION,
            title = context.getString(R.string.error_authentication_title),
            message = context.getString(R.string.error_authentication_message),
            suggestion = context.getString(R.string.error_authentication_suggestion),
            originalError = originalMessage
        )
    }

    private fun createInvalidPasswordError(originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.INVALID_CREDENTIALS,
            title = context.getString(R.string.error_invalid_password_title),
            message = context.getString(R.string.error_invalid_password_message),
            suggestion = context.getString(R.string.error_invalid_password_suggestion),
            originalError = originalMessage
        )
    }

    private fun createRepositoryNotFoundError(originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.REPOSITORY_NOT_FOUND,
            title = context.getString(R.string.error_repository_not_found_title),
            message = context.getString(R.string.error_repository_not_found_message),
            suggestion = context.getString(R.string.error_repository_not_found_suggestion),
            originalError = originalMessage
        )
    }

    private fun createRepositoryCorruptedError(originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.REPOSITORY_CORRUPTED,
            title = context.getString(R.string.error_repository_corrupted_title),
            message = context.getString(R.string.error_repository_corrupted_message),
            suggestion = context.getString(R.string.error_repository_corrupted_suggestion),
            originalError = originalMessage
        )
    }

    private fun createNetworkError(originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.NETWORK,
            title = context.getString(R.string.error_network_title),
            message = context.getString(R.string.error_network_message),
            suggestion = context.getString(R.string.error_network_suggestion),
            originalError = originalMessage
        )
    }

    private fun createTimeoutError(originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.CONNECTION_TIMEOUT,
            title = context.getString(R.string.error_timeout_title),
            message = context.getString(R.string.error_timeout_message),
            suggestion = context.getString(R.string.error_timeout_suggestion),
            originalError = originalMessage
        )
    }

    private fun createStorageFullError(originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.STORAGE_FULL,
            title = context.getString(R.string.error_storage_full_title),
            message = context.getString(R.string.error_storage_full_message),
            suggestion = context.getString(R.string.error_storage_full_suggestion),
            originalError = originalMessage
        )
    }

    private fun createPermissionError(originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.PERMISSION,
            title = context.getString(R.string.error_permission_title),
            message = context.getString(R.string.error_permission_message),
            suggestion = context.getString(R.string.error_permission_suggestion),
            originalError = originalMessage
        )
    }

    private fun createGenericError(rawMessage: String): UserFriendlyError {
        // For unknown errors, provide a sanitized version of the technical error
        val sanitizedMessage = sanitizeErrorMessage(rawMessage)
        return UserFriendlyError(
            category = ErrorCategory.UNKNOWN,
            title = context.getString(R.string.error_generic_title),
            message = context.getString(R.string.error_generic_message, sanitizedMessage),
            suggestion = context.getString(R.string.error_generic_suggestion),
            originalError = rawMessage
        )
    }

    private fun extractRemoteNameFromError(message: String): String {
        // Try to extract remote name from rclone error messages
        val patterns = listOf(
            "'([^']+)'".toRegex(),  // Single quotes
            "\"([^\"]+)\"".toRegex(),  // Double quotes
            "section in config file \\('([^']+)'\\)".toRegex()  // Specific rclone pattern
        )

        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }

        return "unknown remote"
    }

    private fun sanitizeErrorMessage(message: String): String {
        // Remove excessive technical details and sensitive information
        return message
            .replace(Regex("rclone: WARNING:.*$"), "")  // Remove rclone warnings
            .replace(Regex("WARNING:.*$"), "")  // Remove general warnings
            .replace(Regex("CRITICAL:.*$"), "")  // Remove critical markers
            .replace(Regex("\\s+"), " ")  // Normalize whitespace
            .trim()
            .take(200)  // Limit length
            .ifEmpty { "An unexpected error occurred" }
    }
}