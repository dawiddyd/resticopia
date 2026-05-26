package org.dydlakcloud.resticopia.util

import org.dydlakcloud.resticopia.restic.ResticRepo
import org.dydlakcloud.resticopia.restic.ResticRepoRest

/**
 * Utility object for URL-related operations
 */
object UrlUtils {

    /**
     * Sanitizes the repository URL by masking credentials.
     * @param resticRepo The ResticRepo to sanitize.
     * @return A sanitized URL string with credentials masked.
     */
    fun sanitizeRepoUrl(resticRepo: ResticRepo): String {
        if(resticRepo is ResticRepoRest) {
            val regex = """(https?://)([^:@\s]+):([^@\s]+)@""".toRegex()
            return regex.replace(resticRepo.repository()) { matchResult ->
                val protocol = matchResult.groupValues[1]
                val username = matchResult.groupValues[2]
                val password = matchResult.groupValues[3]

                // Username masking logic: Keep 2 letters, mask the rest
                val maskedUsername = if (username.length <= 2) {
                    "*".repeat(username.length)
                } else {
                    username.take(2) + "*".repeat(username.length - 2)
                }

                // Password masking logic: Fully masked using a secure fixed-width mask
                // (Using a fixed width is safer because it hides the actual password length)
                val maskedPassword = "••••••••"

                // Reconstruct: protocol + masked user + colon + fully masked pass + @
                "$protocol$maskedUsername:$maskedPassword@"
            }
        }
        return resticRepo.repository()
    }
}