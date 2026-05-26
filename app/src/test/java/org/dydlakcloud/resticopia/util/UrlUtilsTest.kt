package org.dydlakcloud.resticopia.util

import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import org.dydlakcloud.resticopia.restic.Restic
import org.dydlakcloud.resticopia.restic.ResticRepoRest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.URI

class UrlUtilsTest {

    @MockK
    private lateinit var restic: Restic

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
    }

    @Test
    fun `test sanitize repo Url`() {
        val repo = ResticRepoRest(restic, "", URI.create("https://user:password@host/repo"))
        val sanitizedUrl = UrlUtils.sanitizeRepoUrl(repo)
        assertEquals("rest:https://us**:••••••••@host/repo", sanitizedUrl)
    }

    @Test
    fun `test sanitize repo Url with short username`() {
        val repo = ResticRepoRest(restic, "", URI.create("https://ab:password@host/repo"))
        val sanitizedUrl = UrlUtils.sanitizeRepoUrl(repo)
        assertEquals("rest:https://**:••••••••@host/repo", sanitizedUrl)
    }

    @Test
    fun `test sanitize repo Url without credentials`() {
        val repo = ResticRepoRest(restic, "", URI.create("http://host/repo"))
        val sanitizedUrl = UrlUtils.sanitizeRepoUrl(repo)
        assertEquals("rest:http://host/repo", sanitizedUrl)
    }

    @Test
    fun `test sanitize repo Url with port number`() {
        val repo = ResticRepoRest(restic, "", URI.create("http://my-username:my-password123@192.168.1.11:8080/api"))
        val sanitizedUrl = UrlUtils.sanitizeRepoUrl(repo)
        assertEquals("rest:http://my*********:••••••••@192.168.1.11:8080/api", sanitizedUrl)
    }
}