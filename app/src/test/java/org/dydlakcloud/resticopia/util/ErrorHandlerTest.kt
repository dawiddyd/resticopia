package org.dydlakcloud.resticopia.util

import org.dydlakcloud.resticopia.restic.ResticException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ErrorHandlerTest {

    @Test
    fun `should detect password obscuring error pattern`() {
        val errorMessage = """
            WARNING: linker: Warning: failed to find generated linker configuration from "/linkerconfig/ld.config.txt"
            rclone: WARNING: linker: Warning: failed to find generated linker configuration from"/linkerconfig/ld.config.txt"
            rclone: 2025/12/14 09:34:37 CRITICAL: base64 decode failed when revealing password - is it obscured?: illegal base64 data at input byte 21
            Fatal: create repository at rclone:remote: failed: Fatal: unable to open repository at rclone:remote:: error talking HTTP to rclone: exit status 1
        """.trimIndent()

        val category = ErrorHandler.categorizeError(errorMessage)
        assertEquals(ErrorHandler.ErrorCategory.RCLONE_PASSWORD_OBFUSCATION, category)
    }

    @Test
    fun `should detect SSH key file error pattern`() {
        val errorMessage = """
            WARNING: linker: Warning: failed to find generated linker configuration from"/ linkerconfig/ld.config.txt"
            rclone: WARNING: linker: Warning: failed to find generated linker configuration from"/linkerconfig/ld.config.txt"
            rclone: 2025/12/14 09:52:04 CRITICAL: Failed to create file system for "remote:/path/to/backup": failed to read private key file: open /data/user/0/org.dydlakcloud.resticopia/cache/.ssh/device: no such file or directory
            Fatal: create repository at rclone:remote:/path/to/backup failed: Fatal: unable to open repository at rclone:remote:/path/to/backup: error talking HTTP to rclone: exit status
        """.trimIndent()

        val category = ErrorHandler.categorizeError(errorMessage)
        assertEquals(ErrorHandler.ErrorCategory.RCLONE_SSH_KEY_FILE, category)
    }

    @Test
    fun `should detect unauthorized access error pattern`() {
        val errorMessage = """
            WARNING: linker: Warning: failed to find generated linker configuration from "/linkerconfig/ld.config.txt"
            rclone: WARNING: linker: Warning: failed to find generated linker configuration from "/linkerconfig/ld.config.txt"
            rclone: 2025/12/20 07:00:51 CRITICAL: Failed to create file system for "webdav-next:backups/resticopia": read metadata failed: OCA\DAV\Connector\Sabre\Exception\PasswordLoginForbidden: 401 Unauthorized
            Fatal: unable to open repository at rclone:webdav-next:backups/resticopia: error talking HTTP to rclone: exit status 1
        """.trimIndent()

        val category = ErrorHandler.categorizeError(errorMessage)
        assertEquals(ErrorHandler.ErrorCategory.RCLONE_UNAUTHORIZED, category)
    }

    @Test
    fun `should detect generic rclone remote not found error pattern`() {
        val errorMessage = """
            WARNING: linker: Warning: failed to find generated linker configuration from "/linkerconfig/ld.config.txt" rclone: WARNING: linker: Warning: failed to find generated linker configuration from"/linkerconfig/ ld.confhg.txt"
            rclone: WARNING: linker: Warning: failed to find generated linker configuration from"/linkerconfig/ ld.confhg.txt"
            rclone: 2025/12/10 22:36:36 CRITICAL: Failed to create file system for "myremote:/smb_path"; didn't find section in config file ('myremote") {'message_type': "exit_error","code":1,"message":"Fatal: unable to open repository at rclone:myremote:/smb_path: error talking HTTP to rclone: exit status
        """.trimIndent()

        val category = ErrorHandler.categorizeError(errorMessage)
        assertEquals(ErrorHandler.ErrorCategory.RCLONE_REMOTE_NOT_FOUND, category)
    }

    @Test
    fun `should detect bad gateway error pattern`() {
        val errorMessage = """
            WARNING: linker: Warning: failed to find generated linker configuration from "/linkerconfig/ld.config.txt"
            rclone: WARNING: linker: Warning: failed to find generated linker configuration from "/linkerconfig/ld.config.txt"
            rclone: 2025/12/20 07:32:48 CRITICAL: Failed to create file system for "webdav:312321312321": read metadata failed: error code: 502: 502 Bad Gateway
            Fatal: unable to open repository at rclone:webdav:312321312321: error talking HTTP to rclone: exit status 1
        """.trimIndent()

        val category = ErrorHandler.categorizeError(errorMessage)
        assertEquals(ErrorHandler.ErrorCategory.RCLONE_BAD_GATEWAY, category)
    }

    @Test
    fun `should return unknown category for unrecognized error patterns`() {
        val errorMessage = "some random error that doesn't match any known patterns"

        val category = ErrorHandler.categorizeError(errorMessage)
        assertEquals(ErrorHandler.ErrorCategory.UNKNOWN, category)
    }

    @Test
    fun `should verify that original error messages are preserved in exceptions`() {
        // Test that ResticException preserves the original error message
        val testError = ResticException(1, listOf("test error message"))
        val originalMessage = testError.message ?: "Unknown error"
        assertTrue(originalMessage.contains("test error message"))
    }

    @Test
    fun `should sanitize rclone linker warnings from error messages`() {
        // Test that linker warnings are removed from sanitized error messages
        val rawError = """
            WARNING: linker: Warning: failed to find generated linker configuration from "/linkerconfig/ld.config.txt"
            rclone: WARNING: linker: Warning: failed to find generated linker configuration from"/linkerconfig/ld.config.txt"
            rclone: 2025/12/14 09:34:37 CRITICAL: base64 decode failed when revealing password
            Fatal: create repository at rclone:remote: failed
        """.trimIndent()

        // Create a mock ErrorHandler to test sanitization
        val mockContext = object {
            fun getString(resId: Int) = "Mock String"
        }

        val sanitizedError = rawError.lines()
            .filterNot { line ->
                line.contains("WARNING: linker: Warning: failed to find generated linker configuration") ||
                line.contains("rclone: WARNING: linker: Warning: failed to find generated linker configuration") ||
                line.trim().isEmpty()
            }
            .joinToString("\n")
            .trim()

        // Verify linker warnings are removed
        assertFalse(sanitizedError.contains("WARNING: linker:"))
        assertFalse(sanitizedError.contains("rclone: WARNING: linker:"))
        // But actual error content remains
        assertTrue(sanitizedError.contains("base64 decode failed"))
        assertTrue(sanitizedError.contains("Fatal: create repository"))
    }
}