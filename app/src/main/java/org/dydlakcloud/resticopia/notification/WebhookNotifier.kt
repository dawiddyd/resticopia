package org.dydlakcloud.resticopia.notification

import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.time.Duration

object WebhookNotifier {

    fun sendWebhook(
        webhookUrl: String?,
        onSuccess: Boolean,
        onFailure: Boolean,
        isSuccess: Boolean,
        hostname: String?,
        folderPath: String,
        folderName: String,
        errorMessage: String? = null,
        bearerToken: String? = null,
        duration: Duration? = null
    ): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync {
            if (webhookUrl.isNullOrBlank()) {
                return@supplyAsync
            }

            val shouldSend = if (isSuccess) onSuccess else onFailure
            if (!shouldSend) {
                return@supplyAsync
            }

            val durationString = duration?.let { "${it.seconds}s" }
            val errorString = if (!isSuccess) errorMessage else null

                    val processedUrl = webhookUrl.trim()
                .replace("{success}", isSuccess.toString())
                .replace("{error}", errorString ?: "")
                .replace("{duration}", durationString ?: "")

            Timber.d("Sending to URL: $processedUrl")

            try {
                val url = URL(processedUrl)
                val connection = url.openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")

                    if (!bearerToken.isNullOrBlank()) {
                        connection.setRequestProperty("Authorization", "Bearer $bearerToken")
                    }

                    val device = hostname ?: "Unknown Device"

                    val jsonBody = buildString {
                        append("{")
                        append("\"success\":$isSuccess,")
                        append("\"error\":${errorString?.let { "\"${it.replace("\"", "\\\"")}\"" } ?: "null"},")
                        append("\"duration\":${durationString?.let { "\"$it\"" } ?: "null"},")
                        append("\"device\":\"${device.replace("\"", "\\\"")}\",")
                        append("\"folderName\":\"${folderName.replace("\"", "\\\"")}\",")
                        append("\"folderPath\":\"${folderPath.replace("\"", "\\\"")}\"")
                        append("}")
                    }

                    BufferedOutputStream(connection.outputStream).use { outputStream ->
                        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                            writer.write(jsonBody)
                            writer.flush()
                        }
                    }

                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        Timber.d("Webhook returned HTTP $responseCode")
                    } else {
                        Timber.d("Webhook sent successfully")
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error sending webhook: ${e.message}")
            }
        }
    }
}
