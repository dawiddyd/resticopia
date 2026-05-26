package org.dydlakcloud.resticopia.notification

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import org.dydlakcloud.resticopia.restic.ResticBackupSummary
import org.dydlakcloud.resticopia.restic.ResticSnapshotId
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.time.Duration


class WebhookNotifierTest {

    @MockK
    lateinit var connection: HttpURLConnection
    lateinit var outputStream: ByteArrayOutputStream
    lateinit var summary: ResticBackupSummary

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        outputStream = ByteArrayOutputStream()
        summary = ResticBackupSummary(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130.0,
            ResticSnapshotId("26f2d8ac"))
    }

    @Test
    fun test() {
        mockkObject(WebhookNotifier)
        every { WebhookNotifier.getConnection(any()) } returns connection
        every { connection.getOutputStream() } returns outputStream
        every { connection.responseCode } returns 200
        val feature = WebhookNotifier.sendWebhook("http://localhost/", true, true, true,
            "host-name", "/dcim", "dcim", "Failed to backup",
            "super-secret-token", Duration.ofHours(1), summary)
        feature.join()
        val output = outputStream.toString(Charsets.UTF_8.name())
        val expectedOutput = """
            {"success":true,"error":null,"duration":"3600s","device":"host-name","folderName":"dcim","folderPath":"/dcim","dataAdded":90,"dataAddedPacked":100,"dataBlobs":70,"treeBlobs":80,"dirsUnmodified":60,"dirsChanged":50,"dirsNew":40,"filesUnmodified":30,"filesChanged":20,"filesNew":10,"snapshotId": "26f2d8ac","totalBytesProcessed":"120 B","totalFilesProcessed":110,"totalDuration":130.0}
        """.trimIndent()
        assertEquals(expectedOutput, output)
    }
}