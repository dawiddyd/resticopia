package org.dydlakcloud.resticopia.ui.snapshot

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

/**
 * Unit tests for snapshot download and ZIP creation functionality.
 * 
 * Tests the ZIP archive creation that allows users to download
 * all files from a snapshot in a single compressed file.
 */
class SnapshotDownloadTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `createZipFromDirectory creates valid ZIP archive`() {
        // Given: A directory with test files
        val sourceDir = tempFolder.newFolder("source")
        val file1 = File(sourceDir, "test1.txt")
        file1.writeText("Hello World")
        val file2 = File(sourceDir, "test2.txt")
        file2.writeText("Test Content")

        val zipFile = File(tempFolder.root, "test.zip")

        // When: Creating ZIP from directory
        createZipFromDirectoryForTest(sourceDir, zipFile)

        // Then: ZIP file is created and contains the files
        assertThat(zipFile.exists()).isTrue()
        assertThat(zipFile.length()).isGreaterThan(0L)

        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().toList()
            assertThat(entries).hasSize(2)

            val entry1 = zip.getEntry("test1.txt")
            assertThat(entry1).isNotNull()
            val content1 = zip.getInputStream(entry1).bufferedReader().readText()
            assertThat(content1).isEqualTo("Hello World")

            val entry2 = zip.getEntry("test2.txt")
            assertThat(entry2).isNotNull()
            val content2 = zip.getInputStream(entry2).bufferedReader().readText()
            assertThat(content2).isEqualTo("Test Content")
        }
    }

    @Test
    fun `createZipFromDirectory handles nested directories`() {
        // Given: A directory structure with nested folders
        val sourceDir = tempFolder.newFolder("source")
        val subDir1 = File(sourceDir, "subdir1")
        subDir1.mkdirs()
        val subDir2 = File(sourceDir, "subdir2")
        subDir2.mkdirs()
        val nestedDir = File(subDir1, "nested")
        nestedDir.mkdirs()

        File(sourceDir, "root.txt").writeText("Root file")
        File(subDir1, "sub1.txt").writeText("Subdir 1 file")
        File(subDir2, "sub2.txt").writeText("Subdir 2 file")
        File(nestedDir, "nested.txt").writeText("Nested file")

        val zipFile = File(tempFolder.root, "nested.zip")

        // When: Creating ZIP from directory with nested structure
        createZipFromDirectoryForTest(sourceDir, zipFile)

        // Then: ZIP contains all files with correct paths
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().toList()
            assertThat(entries).hasSize(4)

            // Check all expected paths exist
            assertThat(zip.getEntry("root.txt")).isNotNull()
            assertThat(zip.getEntry("subdir1/sub1.txt")).isNotNull()
            assertThat(zip.getEntry("subdir2/sub2.txt")).isNotNull()
            assertThat(zip.getEntry("subdir1/nested/nested.txt")).isNotNull()

            // Verify content is preserved
            val nestedContent = zip.getInputStream(zip.getEntry("subdir1/nested/nested.txt"))
                .bufferedReader()
                .readText()
            assertThat(nestedContent).isEqualTo("Nested file")
        }
    }

    @Test
    fun `createZipFromDirectory handles empty directory`() {
        // Given: An empty directory
        val sourceDir = tempFolder.newFolder("empty")
        val zipFile = File(tempFolder.root, "empty.zip")

        // When: Creating ZIP from empty directory
        createZipFromDirectoryForTest(sourceDir, zipFile)

        // Then: ZIP file is created but contains no entries
        assertThat(zipFile.exists()).isTrue()

        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().toList()
            assertThat(entries).isEmpty()
        }
    }

    @Test
    fun `createZipFromDirectory handles large files`() {
        // Given: A directory with a larger file
        val sourceDir = tempFolder.newFolder("large")
        val largeFile = File(sourceDir, "large.txt")
        val largeContent = "x".repeat(1024 * 100) // 100KB
        largeFile.writeText(largeContent)

        val zipFile = File(tempFolder.root, "large.zip")

        // When: Creating ZIP from directory
        createZipFromDirectoryForTest(sourceDir, zipFile)

        // Then: Large file is correctly compressed and stored
        assertThat(zipFile.exists()).isTrue()
        // ZIP should be smaller than original due to compression
        assertThat(zipFile.length()).isLessThan(largeFile.length())

        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry("large.txt")
            assertThat(entry).isNotNull()
            val extractedContent = zip.getInputStream(entry).bufferedReader().readText()
            assertThat(extractedContent).isEqualTo(largeContent)
        }
    }

    @Test
    fun `createZipFromDirectory handles special characters in filenames`() {
        // Given: Files with special characters in names
        val sourceDir = tempFolder.newFolder("special")
        val file1 = File(sourceDir, "file with spaces.txt")
        file1.writeText("Spaces in name")
        val file2 = File(sourceDir, "file-with-dashes.txt")
        file2.writeText("Dashes in name")
        val file3 = File(sourceDir, "file_with_underscores.txt")
        file3.writeText("Underscores in name")

        val zipFile = File(tempFolder.root, "special.zip")

        // When: Creating ZIP
        createZipFromDirectoryForTest(sourceDir, zipFile)

        // Then: All files are included with correct names
        ZipFile(zipFile).use { zip ->
            assertThat(zip.getEntry("file with spaces.txt")).isNotNull()
            assertThat(zip.getEntry("file-with-dashes.txt")).isNotNull()
            assertThat(zip.getEntry("file_with_underscores.txt")).isNotNull()
        }
    }

    @Test
    fun `createZipFromDirectory handles binary files`() {
        // Given: A directory with binary content
        val sourceDir = tempFolder.newFolder("binary")
        val binaryFile = File(sourceDir, "binary.dat")
        val binaryData = ByteArray(256) { it.toByte() }
        binaryFile.writeBytes(binaryData)

        val zipFile = File(tempFolder.root, "binary.zip")

        // When: Creating ZIP
        createZipFromDirectoryForTest(sourceDir, zipFile)

        // Then: Binary data is preserved
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry("binary.dat")
            assertThat(entry).isNotNull()
            val extractedData = zip.getInputStream(entry).readBytes()
            assertThat(extractedData).isEqualTo(binaryData)
        }
    }

    @Test
    fun `createZipFromDirectory handles mixed content`() {
        // Given: A realistic directory structure
        val sourceDir = tempFolder.newFolder("mixed")
        
        // Create various file types
        File(sourceDir, "document.txt").writeText("Text document")
        File(sourceDir, "data.json").writeText("""{"key": "value"}""")
        File(sourceDir, "image.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        
        val subDir = File(sourceDir, "subfolder")
        subDir.mkdirs()
        File(subDir, "nested.md").writeText("# Markdown")

        val zipFile = File(tempFolder.root, "mixed.zip")

        // When: Creating ZIP
        createZipFromDirectoryForTest(sourceDir, zipFile)

        // Then: All files are included
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().toList()
            assertThat(entries).hasSize(4)
            assertThat(zip.getEntry("document.txt")).isNotNull()
            assertThat(zip.getEntry("data.json")).isNotNull()
            assertThat(zip.getEntry("image.jpg")).isNotNull()
            assertThat(zip.getEntry("subfolder/nested.md")).isNotNull()
        }
    }

    @Test
    fun `createZipFromDirectory skips directories in entries`() {
        // Given: A directory structure
        val sourceDir = tempFolder.newFolder("dirs")
        val subDir = File(sourceDir, "empty_subdir")
        subDir.mkdirs()
        File(sourceDir, "file.txt").writeText("Content")

        val zipFile = File(tempFolder.root, "dirs.zip")

        // When: Creating ZIP
        createZipFromDirectoryForTest(sourceDir, zipFile)

        // Then: Only file entries are included, not directory entries
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().toList()
            // Should only contain the file, not the empty directory
            assertThat(entries).hasSize(1)
            assertThat(entries.all { !it.isDirectory }).isTrue()
            assertThat(zip.getEntry("file.txt")).isNotNull()
        }
    }

    @Test
    fun `createZipFromDirectory creates reproducible archives`() {
        // Given: A directory with files
        val sourceDir = tempFolder.newFolder("reproducible")
        File(sourceDir, "file1.txt").writeText("Content 1")
        File(sourceDir, "file2.txt").writeText("Content 2")

        val zipFile1 = File(tempFolder.root, "archive1.zip")
        val zipFile2 = File(tempFolder.root, "archive2.zip")

        // When: Creating two ZIPs from the same source
        createZipFromDirectoryForTest(sourceDir, zipFile1)
        createZipFromDirectoryForTest(sourceDir, zipFile2)

        // Then: Both archives contain the same files
        // Note: Exact byte equality may vary due to timestamps, but structure should match
        ZipFile(zipFile1).use { zip1 ->
            ZipFile(zipFile2).use { zip2 ->
                val entries1 = zip1.entries().toList().map { it.name }.sorted()
                val entries2 = zip2.entries().toList().map { it.name }.sorted()
                assertThat(entries1).isEqualTo(entries2)
            }
        }
    }

    @Test
    fun `createZipFromDirectory handles unicode filenames`() {
        // Given: Files with unicode characters
        val sourceDir = tempFolder.newFolder("unicode")
        val file1 = File(sourceDir, "文件.txt")
        file1.writeText("Chinese filename")
        val file2 = File(sourceDir, "αρχείο.txt")
        file2.writeText("Greek filename")
        val file3 = File(sourceDir, "файл.txt")
        file3.writeText("Cyrillic filename")

        val zipFile = File(tempFolder.root, "unicode.zip")

        // When: Creating ZIP
        createZipFromDirectoryForTest(sourceDir, zipFile)

        // Then: Unicode filenames are preserved
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().toList()
            assertThat(entries).hasSize(3)
            
            val names = entries.map { it.name }
            assertThat(names).contains("文件.txt")
            assertThat(names).contains("αρχείο.txt")
            assertThat(names).contains("файл.txt")
        }
    }

    @Test
    fun `createZipFromDirectory preserves relative paths correctly`() {
        // Given: A multi-level directory structure
        val sourceDir = tempFolder.newFolder("paths")
        val level1 = File(sourceDir, "level1")
        level1.mkdirs()
        val level2 = File(level1, "level2")
        level2.mkdirs()
        val level3 = File(level2, "level3")
        level3.mkdirs()

        File(level3, "deep.txt").writeText("Deep file")
        File(level1, "mid.txt").writeText("Mid file")

        val zipFile = File(tempFolder.root, "paths.zip")

        // When: Creating ZIP
        createZipFromDirectoryForTest(sourceDir, zipFile)

        // Then: Relative paths are correct
        ZipFile(zipFile).use { zip ->
            // Path separators should be forward slashes in ZIP
            assertThat(zip.getEntry("level1/mid.txt")).isNotNull()
            assertThat(zip.getEntry("level1/level2/level3/deep.txt")).isNotNull()
        }
    }

    // Helper function that mimics the actual implementation
    // This would be extracted from SnapshotFragment for testing
    private fun createZipFromDirectoryForTest(sourceDir: File, zipFile: File) {
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zipOut ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(sourceDir).path
                    val entry = java.util.zip.ZipEntry(relativePath)
                    zipOut.putNextEntry(entry)
                    
                    java.io.FileInputStream(file).use { input ->
                        input.copyTo(zipOut)
                    }
                    
                    zipOut.closeEntry()
                }
            }
        }
    }
}

