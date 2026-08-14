package org.dydlakcloud.resticopia.config

import android.util.Base64
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File


class PortableConfigTest {

    @After
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun `Test toConfig on tags`() {
        val portableConfig = PortableConfig(folders = listOf(PortableFolderConfig(FolderConfigId.create().uuid.toString(),RepoConfigId.create().uuid.toString(), "path", "schedule", tags = listOf("tag0", "tag1")),
                                                             PortableFolderConfig(FolderConfigId.create().uuid.toString(),RepoConfigId.create().uuid.toString(), "path", "schedule", tags = listOf("tag2", "tag3"))))

        val config = portableConfig.toConfig()
        assertEquals("tag0", config.folders[0].tags[0])
        assertEquals("tag1", config.folders[0].tags[1])
        assertEquals("tag2", config.folders[1].tags[0])
        assertEquals("tag3", config.folders[1].tags[1])
    }

    @Test
    fun `Test fromConfig on tags`() {
        val folders = listOf(mockk<FolderConfig>())
        every { folders[0].id } returns FolderConfigId.create()
        every { folders[0].repoId } returns RepoConfigId.create()
        every { folders[0].path } returns File("/tmp/folder_0")
        every { folders[0].keepLast } returns 1
        every { folders[0].schedule } returns "Daily"
        every { folders[0].keepWithin } returns null
        every { folders[0].history } returns emptyList()
        every { folders[0].tags } returns listOf("tag0", "tag1")
        val config = Config(emptyList(), folders, "host-name", emptyList())

        mockkStatic(Base64::class)
        every { Base64.encodeToString(any<ByteArray>(), Base64.NO_WRAP) } returns "mockedBase64String"

        val portableConfig = PortableConfig.fromConfig(config, "12345678", false, false)

        assertTrue("tag0", portableConfig.folders.isEmpty())
        verify(exactly = 1) { folders[0].tags }
    }

}