package org.dydlakcloud.resticopia.util

import android.content.Context
import org.dydlakcloud.resticopia.restic.ResticException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ErrorHandlerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val errorHandler = ErrorHandler(context)

    @Test
    fun `should categorize rclone config error correctly`() {
        val rcloneError = ResticException(
            1,
            listOf(
                "WARNING: linker: Warning: failed to find generated linker configuration from \"/linkerconfig/ld.config.txt\" rclone: WARNING: linker: Warning: failed to find generated linker configuration from\"/linkerconfig/ ld.confhg.txt\"",
                "rclone: 2025/12/10 22:36:36 CRITICAL: Failed to create file system for \"myremote:/smb_path\"; didn't find section in config file ('myremote\") {'message_type': \"exit_error\", \"code\":1, \"message\":\"Fatal: unable to open repository at rclone:myremote:/smb_path: error talking HTTP to rclone: exit status``"
            )
        )

        val result = errorHandler.getUserFriendlyError(rcloneError)

        assertEquals(ErrorHandler.ErrorCategory.RCLONE_REMOTE_NOT_FOUND, result.category)
        assertTrue(result.title.contains("Rclone") || result.title.contains("Remote"))
        assertTrue(result.message.contains("myremote") || result.message.contains("remote"))
        assertTrue(result.suggestion?.isNotEmpty() == true)
    }

    @Test
    fun `should handle invalid password error`() {
        val passwordError = ResticException(1, listOf("wrong password"))

        val result = errorHandler.getUserFriendlyError(passwordError)

        assertEquals(ErrorHandler.ErrorCategory.INVALID_CREDENTIALS, result.category)
        assertTrue(result.title.contains("Password"))
        assertTrue(result.message.contains("incorrect") || result.message.contains("wrong"))
    }

    @Test
    fun `should handle repository not found error`() {
        val repoError = ResticException(1, listOf("repository does not exist"))

        val result = errorHandler.getUserFriendlyError(repoError)

        assertEquals(ErrorHandler.ErrorCategory.REPOSITORY_NOT_FOUND, result.category)
        assertTrue(result.title.contains("Repository") || result.title.contains("Found"))
    }

    @Test
    fun `should provide generic error for unknown errors`() {
        val unknownError = ResticException(1, listOf("some random error that doesn't match patterns"))

        val result = errorHandler.getUserFriendlyError(unknownError)

        assertEquals(ErrorHandler.ErrorCategory.UNKNOWN, result.category)
        assertTrue(result.message.contains("some random error"))
    }
}