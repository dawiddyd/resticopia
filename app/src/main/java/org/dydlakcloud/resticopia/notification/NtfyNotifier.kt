package org.dydlakcloud.resticopia.notification

import java.io.BufferedOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

/**
 * Service for sending notifications to ntfy.
 * Follows the project's pattern of using CompletableFuture for async operations.
 */
object NtfyNotifier {
    
    /**
     * Sends a notification to the configured ntfy URL.
     * 
     * @param ntfyUrl The full ntfy URL (e.g., "https://ntfy.sh/mytopic")
     * @param message The notification message to send
     * @return CompletableFuture that completes when the notification is sent
     */
    fun sendNotification(ntfyUrl: String?, message: String): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync {
            if (ntfyUrl.isNullOrBlank()) {
                // Silently skip if ntfy URL is not configured
                return@supplyAsync
            }

            try {
                val url = URL(ntfyUrl.trim())
                val connection = url.openConnection() as HttpURLConnection
                
                try {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.connectTimeout = 10000 // 10 seconds
                    connection.readTimeout = 10000
                    connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                    
                    // Write message to output stream
                    BufferedOutputStream(connection.outputStream).use { outputStream ->
                        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                            writer.write(message)
                            writer.flush()
                        }
                    }
                    
                    // Check response code
                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        println("NtfyNotifier: Failed to send notification. HTTP $responseCode")
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                // Log error but don't fail the backup
                e.printStackTrace()
                println("NtfyNotifier: Error sending notification: ${e.message}")
            }
        }
    }
    
    /**
     * Sends a success notification for a backup.
     *
     * @param ntfyUrl The full ntfy URL
     * @param hostname The device hostname for identification
     * @param folderPath The path of the backed up folder
     * @return CompletableFuture that completes when the notification is sent
     */
    fun sendBackupSuccessNotification(ntfyUrl: String?, hostname: String?, folderPath: String): CompletableFuture<Unit> {
        val device = hostname ?: "Unknown Device"
        val message = "✅ $device - Backup successful: $folderPath"
        return sendNotification(ntfyUrl, message)
    }

    /**
     * Sends a failure notification for a backup.
     *
     * @param ntfyUrl The full ntfy URL
     * @param hostname The device hostname for identification
     * @param folderPath The path of the folder that failed to backup
     * @param error The error message
     * @return CompletableFuture that completes when the notification is sent
     */
    fun sendBackupFailureNotification(ntfyUrl: String?, hostname: String?, folderPath: String, error: String): CompletableFuture<Unit> {
        val device = hostname ?: "Unknown Device"
        val message = "❌ $device - Backup failed: $folderPath\nError: $error"
        return sendNotification(ntfyUrl, message)
    }
}

