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
        RCLONE_PASSWORD_OBFUSCATION,
        RCLONE_SSH_KEY_FILE,
        RCLONE_UNAUTHORIZED,
        RCLONE_BAD_GATEWAY,
        UNKNOWN
    }

    companion object {
        // Extract pattern matching logic to avoid code duplication in tests
        fun categorizeError(errorMessage: String): ErrorCategory {
            return when {
                // Most specific rclone errors first
                isRcloneRemoteNotFoundError(errorMessage) -> ErrorCategory.RCLONE_REMOTE_NOT_FOUND
                isPasswordObscuringError(errorMessage) -> ErrorCategory.RCLONE_PASSWORD_OBFUSCATION
                isSSHKeyFileError(errorMessage) -> ErrorCategory.RCLONE_SSH_KEY_FILE
                isUnauthorizedError(errorMessage) -> ErrorCategory.RCLONE_UNAUTHORIZED
                isBadGatewayError(errorMessage) -> ErrorCategory.RCLONE_BAD_GATEWAY
                isRcloneConfigError(errorMessage) -> ErrorCategory.RCLONE_CONFIG

                // Authentication errors
                isAuthenticationError(errorMessage) -> ErrorCategory.AUTHENTICATION
                isInvalidPasswordError(errorMessage) -> ErrorCategory.INVALID_CREDENTIALS

                // Repository errors
                isRepositoryNotFoundError(errorMessage) -> ErrorCategory.REPOSITORY_NOT_FOUND
                isRepositoryCorruptedError(errorMessage) -> ErrorCategory.REPOSITORY_CORRUPTED

                // Network errors
                isNetworkError(errorMessage) -> ErrorCategory.NETWORK
                isTimeoutError(errorMessage) -> ErrorCategory.CONNECTION_TIMEOUT

                // Storage errors
                isStorageFullError(errorMessage) -> ErrorCategory.STORAGE_FULL

                // Permission errors
                isPermissionError(errorMessage) -> ErrorCategory.PERMISSION

                // Default fallback
                else -> ErrorCategory.UNKNOWN
            }
        }

        private fun isPasswordObscuringError(message: String): Boolean {
            return message.contains("base64 decode failed when revealing password") ||
                   message.contains("is it obscured?: illegal base64 data") ||
                   message.contains("input too short when revealing password")
        }

        private fun isSSHKeyFileError(message: String): Boolean {
            return message.contains("failed to read private key file") ||
                   message.contains("no such file or directory") &&
                   (message.contains("ssh") || message.contains("private key"))
        }

        private fun isUnauthorizedError(message: String): Boolean {
            return message.contains("401 Unauthorized") ||
                   message.contains("PasswordLoginForbidden") ||
                   (message.contains("unauthorized") && message.contains("rclone"))
        }

        private fun isBadGatewayError(message: String): Boolean {
            return message.contains("502 Bad Gateway") ||
                   message.contains("502") && message.contains("Bad Gateway")
        }

        private fun isRcloneConfigError(message: String): Boolean {
            return message.contains("failed to find generated linker configuration")
        }

        private fun isRcloneRemoteNotFoundError(message: String): Boolean {
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

        // Sanitize the error message by removing generic linker warnings
        val sanitizedMessage = sanitizeRcloneError(originalMessage)

        // Use the companion object's categorization logic
        val category = categorizeError(originalMessage)
        return when (category) {
            ErrorCategory.RCLONE_CONFIG -> createRcloneConfigError(sanitizedMessage).copy(originalError = originalMessage)
            ErrorCategory.RCLONE_REMOTE_NOT_FOUND -> createRcloneRemoteNotFoundError(extractRemoteNameFromError(originalMessage), sanitizedMessage).copy(originalError = originalMessage)
            ErrorCategory.AUTHENTICATION -> createAuthenticationError(sanitizedMessage).copy(originalError = originalMessage)
            ErrorCategory.INVALID_CREDENTIALS -> createInvalidPasswordError(sanitizedMessage).copy(originalError = originalMessage)
            ErrorCategory.REPOSITORY_NOT_FOUND -> createRepositoryNotFoundError(sanitizedMessage).copy(originalError = originalMessage)
            ErrorCategory.REPOSITORY_CORRUPTED -> createRepositoryCorruptedError(sanitizedMessage).copy(originalError = originalMessage)
            ErrorCategory.NETWORK -> createNetworkError(sanitizedMessage).copy(originalError = originalMessage)
            ErrorCategory.CONNECTION_TIMEOUT -> createTimeoutError(sanitizedMessage).copy(originalError = originalMessage)
            ErrorCategory.STORAGE_FULL -> createStorageFullError(sanitizedMessage).copy(originalError = originalMessage)
            ErrorCategory.PERMISSION -> createPermissionError(sanitizedMessage).copy(originalError = originalMessage)
            ErrorCategory.RCLONE_PASSWORD_OBFUSCATION -> createPasswordObscuringError(sanitizedMessage).copy(originalError = originalMessage)
            ErrorCategory.RCLONE_SSH_KEY_FILE -> createSSHKeyFileError(sanitizedMessage).copy(originalError = originalMessage)
            ErrorCategory.RCLONE_UNAUTHORIZED -> createUnauthorizedError(sanitizedMessage).copy(originalError = originalMessage)
            ErrorCategory.RCLONE_BAD_GATEWAY -> createBadGatewayError(sanitizedMessage).copy(originalError = originalMessage)
            ErrorCategory.CONFIGURATION -> createRcloneConfigError(sanitizedMessage).copy(originalError = originalMessage) // Fallback
            else -> createGenericError(sanitizedMessage).copy(originalError = originalMessage)
        }
    }

    // Method to get error category using the shared companion object logic
    fun getErrorCategory(errorMessage: String): ErrorCategory {
        return categorizeError(errorMessage)
    }

    // Sanitize rclone error messages by removing generic linker warnings
    private fun sanitizeRcloneError(errorMessage: String): String {
        return errorMessage.lines()
            .filterNot { line ->
                line.contains("WARNING: linker: Warning: failed to find generated linker configuration") ||
                line.contains("rclone: WARNING: linker: Warning: failed to find generated linker configuration") ||
                line.trim().isEmpty()
            }
            .joinToString("\n")
            .trim()
    }

    private fun isPasswordObscuringError(message: String): Boolean {
        return message.contains("base64 decode failed when revealing password") ||
               message.contains("is it obscured?: illegal base64 data") ||
               message.contains("input too short when revealing password")
    }

    private fun isSSHKeyFileError(message: String): Boolean {
        return message.contains("failed to read private key file") ||
               message.contains("no such file or directory") &&
               (message.contains("ssh") || message.contains("private key"))
    }

    private fun isUnauthorizedError(message: String): Boolean {
        return message.contains("401 Unauthorized") ||
               message.contains("PasswordLoginForbidden") ||
               (message.contains("unauthorized") && message.contains("rclone"))
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

    private fun createPasswordObscuringError(originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.RCLONE_PASSWORD_OBFUSCATION,
            title = context.getString(R.string.error_rclone_password_obscuring_title),
            message = context.getString(R.string.error_rclone_password_obscuring_message),
            suggestion = context.getString(R.string.error_rclone_password_obscuring_suggestion),
            originalError = originalMessage
        )
    }

    private fun createSSHKeyFileError(originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.RCLONE_SSH_KEY_FILE,
            title = context.getString(R.string.error_rclone_ssh_key_title),
            message = context.getString(R.string.error_rclone_ssh_key_message),
            suggestion = context.getString(R.string.error_rclone_ssh_key_suggestion),
            originalError = originalMessage
        )
    }

    private fun createUnauthorizedError(originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.RCLONE_UNAUTHORIZED,
            title = context.getString(R.string.error_rclone_unauthorized_title),
            message = context.getString(R.string.error_rclone_unauthorized_message),
            suggestion = context.getString(R.string.error_rclone_unauthorized_suggestion),
            originalError = originalMessage
        )
    }

    private fun createBadGatewayError(originalMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.RCLONE_BAD_GATEWAY,
            title = context.getString(R.string.error_rclone_bad_gateway_title),
            message = context.getString(R.string.error_rclone_bad_gateway_message),
            suggestion = context.getString(R.string.error_rclone_bad_gateway_suggestion),
            originalError = originalMessage
        )
    }

    private fun createGenericError(rawMessage: String): UserFriendlyError {
        return UserFriendlyError(
            category = ErrorCategory.UNKNOWN,
            title = context.getString(R.string.error_generic_title),
            message = context.getString(R.string.error_generic_message, rawMessage),
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
}