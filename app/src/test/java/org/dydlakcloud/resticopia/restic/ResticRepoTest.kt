package org.dydlakcloud.resticopia.restic

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Duration
import java.util.concurrent.CompletableFuture


class ResticRepoTest {

    @MockK
    lateinit var restic: Restic
    @MockK
    lateinit var file0: File
    @MockK
    lateinit var file1: File
    @MockK
    lateinit var file2: File

    lateinit var resticRepo: ResticRepo

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        every { restic.hostname } returns "pixel-7-pro"
        every { file0.absolutePath } returns "/path/to/file0"
        every { file1.absolutePath } returns "/path/to/file1"
        every { file2.absolutePath } returns "/path/to/file2"
        resticRepo = object: ResticRepo(restic = restic, password = "password") {
            override fun repository(): String {
                return "repository"
            }
        }
    }

    @Test
    fun `validate object create`() {
        assertNotNull(resticRepo)
    }

    @Test
    fun `verify restic-ls() fun for -- long argument`() {
        val resticSnapshotId = ResticSnapshotId("snapshot-id")

        val mockOutput = listOf(
                  """
                      {"time":"2026-05-14T23:52:29.927406296Z","parent":"114ce85ebcb4bba18003352cae7a59ead5023e90d8b5e312be8e4a962d13cd6f","tree":"0aba9705eb879694e8c5022a3d5cd09dacb56e94fa8e21b856d2b26b2015e045","paths":["/storage/emulated/0/DCIM"],"hostname":"pixel-7-pro","username":"android","uid":10977,"gid":10977,"tags":["/dcim","scheduled"],"program_version":"restic 2025-12-23T19:18:43Z","summary":{"backup_start":"2026-05-14T23:52:29.927406296Z","backup_end":"2026-05-14T23:52:35.335416838Z","files_new":0,"files_changed":0,"files_unmodified":3,"dirs_new":0,"dirs_changed":0,"dirs_unmodified":9,"data_blobs":0,"tree_blobs":0,"data_added":0,"data_added_packed":0,"total_files_processed":3,"total_bytes_processed":4640360},"id":"3e5db3e4d76d5fe10d3d69e6d471ba8ff8f4225b7a2115e6d9bcad57f7d5e845","short_id":"3e5db3e4","message_type":"snapshot","struct_type":"snapshot"} 
                  """.trimIndent(),
                  """
                      {"name":"storage","type":"dir","path":"/storage","uid":2000,"gid":9997,"mode":2147484104,"permissions":"drwx--x---","mtime":"2026-05-14T10:00:55.108000002Z","atime":"2026-05-14T10:00:55.108000002Z","ctime":"2026-05-14T10:01:11.27200001Z","inode":13,"message_type":"node","struct_type":"node"}
                  """.trimIndent(),
                  """
                      {"name":"emulated","type":"dir","path":"/storage/emulated","uid":1023,"gid":1023,"mode":2147484008,"permissions":"dr-xr-x---","mtime":"2026-03-19T11:11:01.406433741Z","atime":"2026-03-19T11:11:01.406433741Z","ctime":"2026-03-19T11:11:01.406433741Z","inode":241,"message_type":"node","struct_type":"node"}
                  """.trimIndent(),
                  """  
                      {"name":"0","type":"dir","path":"/storage/emulated/0","uid":1023,"gid":1023,"mode":2151678456,"permissions":"dgrwxrwx---","mtime":"2026-05-01T15:42:38.180202286Z","atime":"2026-05-01T15:42:38.180202286Z","ctime":"2026-05-14T10:02:04.548000036Z","inode":3234,"message_type":"node","struct_type":"node"}
                  """.trimIndent(),
                  """        
                    {"name":"DCIM","type":"dir","path":"/storage/emulated/0/DCIM","uid":10238,"gid":1023,"mode":2151678456,"permissions":"dgrwxrwx---","mtime":"2026-02-02T12:12:31.066466102Z","atime":"2026-02-02T12:12:31.066466102Z","ctime":"2026-02-02T12:12:31.066466102Z","inode":4595,"message_type":"node","struct_type":"node"}
                  """.trimIndent(),
                  """  
                    {"name":".stfolder","type":"dir","path":"/storage/emulated/0/DCIM/.stfolder","uid":10238,"gid":1023,"mode":2151678456,"permissions":"dgrwxrwx---","mtime":"2023-05-17T11:14:02.072327473Z","atime":"2023-05-17T11:14:02.072327473Z","ctime":"2023-05-22T11:42:43.660002342Z","inode":42194,"message_type":"node","struct_type":"node"}
                  """.trimIndent(),
                  """              
                    {"name":"Camera","type":"dir","path":"/storage/emulated/0/DCIM/Camera","uid":10238,"gid":1023,"mode":2151678456,"permissions":"dgrwxrwx---","mtime":"2026-05-13T16:00:06.813103953Z","atime":"2026-05-13T16:00:06.813103953Z","ctime":"2026-05-13T16:00:06.813103953Z","inode":15175,"message_type":"node","struct_type":"node"}
                  """.trimIndent(),
                  """  
                    {"name":"PXL_20260209_103940855.jpg","type":"file","path":"/storage/emulated/0/DCIM/Camera/PXL_20260209_103940855.jpg","uid":10238,"gid":1023,"size":2110429,"mode":504,"permissions":"-rwxrwx---","mtime":"2026-02-09T10:39:50.468734062Z","atime":"2026-02-09T10:39:50.468734062Z","ctime":"2026-03-19T12:15:17.899740314Z","inode":65917,"message_type":"node","struct_type":"node"}
                  """.trimIndent(),
                  """            
                    {"name":"PXL_20260512_144011149.jpg","type":"file","path":"/storage/emulated/0/DCIM/Camera/PXL_20260512_144011149.jpg","uid":10238,"gid":1023,"size":2103546,"mode":504,"permissions":"-rwxrwx---","mtime":"2026-05-12T14:40:14.714070361Z","atime":"2026-05-12T14:40:14.714070361Z","ctime":"2026-05-12T14:40:14.962070361Z","inode":253770,"message_type":"node","struct_type":"node"}
                  """.trimIndent(),
                  """             
                    {"name":"Folder","type":"dir","path":"/storage/emulated/0/DCIM/Folder","uid":10238,"gid":1023,"mode":2151678456,"permissions":"dgrwxrwx---","mtime":"2026-05-01T12:20:06.280196352Z","atime":"2026-05-01T12:20:06.280196352Z","ctime":"2026-05-01T12:20:06.280196352Z","inode":142257,"message_type":"node","struct_type":"node"}
                  """.trimIndent(),
                  """
                    {"name":"Cloud_Edited_01_05_2026_13_19_47_birds-perched-animals-feathers-4580620.jpg","type":"file","path":"/storage/emulated/0/DCIM/Folder/Cloud_Edited_01_05_2026_13_19_47_birds-perched-animals-feathers-4580620.jpg","uid":10238,"gid":1023,"size":426385,"mode":504,"permissions":"-rwxrwx---","mtime":"2026-05-01T12:20:06.104196352Z","atime":"2026-05-01T12:20:06.104196352Z","ctime":"2026-05-01T15:40:15.872202216Z","inode":261407,"message_type":"node","struct_type":"node"} 
                  """.trimIndent(),
                  """             
                    {"name":"InskamOTG","type":"dir","path":"/storage/emulated/0/DCIM/InskamOTG","uid":10238,"gid":1023,"mode":2151678456,"permissions":"dgrwxrwx---","mtime":"2026-03-27T16:41:07.579258827Z","atime":"2026-03-27T16:41:07.579258827Z","ctime":"2026-03-27T16:41:07.579258827Z","inode":259642,"message_type":"node","struct_type":"node"}
                  """.trimIndent(),
                  """  
                    {"name":"UseePlus","type":"dir","path":"/storage/emulated/0/DCIM/UseePlus","uid":10238,"gid":1023,"mode":2151678456,"permissions":"dgrwxrwx---","mtime":"2026-01-23T11:26:58.284901829Z","atime":"2026-01-23T11:26:58.284901829Z","ctime":"2026-01-23T16:44:12.836431136Z","inode":234121,"message_type":"node","struct_type":"node"}
                  """.trimIndent(),
        )

        every {
            restic.restic(listOf("--json", "ls", "snapshot-id", "--long"), any(), any(), any(), any(), any())
        } returns CompletableFuture.completedFuture(Pair(mockOutput, emptyList()))

        val files = arrayListOf<ResticFile>()
        resticRepo.ls(resticSnapshotId).handle { lsResult, throwable ->
            assertNotNull(lsResult)
            assertNull(throwable)

            lsResult?.second?.let {
                files.addAll(it)
            }
        }.join()
        assertEquals(12, files.size)

        assertEquals(files[0].name, 0L, files[0].size)
        assertEquals(files[0].name, "dir", files[0].type)

        assertEquals("PXL_20260209_103940855.jpg", files[6].name)
        assertEquals(files[6].name, 2110429L, files[6].size)
        assertEquals("2.01 MiB", files[6].humanReadableSize)

        assertEquals("PXL_20260512_144011149.jpg", files[7].name)
        assertEquals(files[7].name, 2103546L, files[7].size)
        assertEquals("2.01 MiB", files[7].humanReadableSize)

        assertEquals("Cloud_Edited_01_05_2026_13_19_47_birds-perched-animals-feathers-4580620.jpg", files[9].name)
        assertEquals(files[9].name, 426385L, files[9].size)
        assertEquals("416.39 KiB", files[9].humanReadableSize)
    }

    @Test
    fun `validate forget`() {
        val sampleResticJson = """
        [
          {
            "group": {
              "host": "pixel-7-pro",
              "paths": "/home/user/projects"
            },
            "keep": [
              {
                "id": "3a1b2c3d",
                "time": "2026-05-21T12:00:00.000Z",
                "tree": "a6fd83c2e1b94d76a213e45fbc89d0123e456789abcdef0123456789abcdef12",
                "host": "pixel-7-pro",
                "hostname": "pixel-7-pro",
                "paths": ["/home/user/projects"],
                "reasons": ["keep-daily"]
              }
            ],
            "remove": [
              {
                "id": "9f8e7d6c",
                "time": "2026-05-14T12:00:00.000Z",
                "tree": "b7ec83c2e1b94d76a213e45fbc89d0123e456789abcdef0123456789abcdef34",
                "host": "pixel-7-pro",
                "hostname": "pixel-7-pro",
                "paths": ["/home/user/projects"]
              }
            ]
          }
        ]
        """.trimIndent()
        every {
            restic.restic(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns CompletableFuture.completedFuture(Pair(listOf(sampleResticJson), emptyList()))
        val paths = listOf(file0, file1)
        var forgetResult: List<ResticSnapshot>? = null
        var forgetException: Throwable? = null
        resticRepo.forget(paths, 1, Duration.ZERO, true).handle { result, exception ->
            forgetResult = result
            forgetException = exception
        }.join()
        assertNotNull(forgetResult)
        assertEquals(1, forgetResult?.size)
        assertNotNull(forgetResult?.get(0))
        val resticSnapshot = forgetResult?.get(0)!!
        assertEquals("b7ec83c2e1b94d76a213e45fbc89d0123e456789abcdef0123456789abcdef34", resticSnapshot.tree)
        assertEquals("9f8e7d6c", resticSnapshot.id.id)
        assertEquals("pixel-7-pro", resticSnapshot.hostname)
        assertEquals(0, resticSnapshot.tags.size)
        assertEquals(1, resticSnapshot.paths.size)
        assertEquals("/home/user/projects", resticSnapshot.paths[0].absolutePath)

        assertNull(forgetException)
    }
}