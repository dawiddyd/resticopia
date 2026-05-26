package org.dydlakcloud.resticopia.restic

import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.ZonedDateTime

class ResticFileTest {
    @MockK
    private lateinit var mokkFile: File

    lateinit var resticFile: ResticFile

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        resticFile = ResticFile(
            name = "file.txt",
            type = "file",
            size = 1024L,
            path = mokkFile,
            mtime = ZonedDateTime.parse("2024-01-01T00:00:00Z"),
            atime = ZonedDateTime.parse("2024-01-01T00:00:00Z"),
            ctime = ZonedDateTime.parse("2024-01-01T00:00:00Z")
        )
    }

    @Test
    fun `verify human readable size`() {
        assertNotNull(resticFile)
        assertEquals(mokkFile, resticFile.path)
        assertEquals(1024L, resticFile.size)
        assertEquals("1.00 KiB", resticFile.humanReadableSize)
    }
}