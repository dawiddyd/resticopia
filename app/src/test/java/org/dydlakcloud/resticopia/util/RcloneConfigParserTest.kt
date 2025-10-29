package org.dydlakcloud.resticopia.util

import com.google.common.truth.Truth.assertThat
import org.dydlakcloud.resticopia.fixtures.TestFixtures
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for RcloneConfigParser.
 * Tests parsing of rclone configuration files.
 * 
 * Uses TemporaryFolder for file-based tests to ensure clean test isolation.
 */
class RcloneConfigParserTest {
    
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `parseConfigContent parses single remote correctly`() {
        // Given: A simple rclone config with one remote
        val config = """
            [webdav-server]
            type = webdav
            url = http://localhost:8080/dav
            vendor = other
        """.trimIndent()

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: One remote is found
        assertThat(remotes).hasSize(1)
        assertThat(remotes[0].name).isEqualTo("webdav-server")
        assertThat(remotes[0].type).isEqualTo("webdav")
    }

    @Test
    fun `parseConfigContent parses multiple remotes correctly`() {
        // Given: A config with multiple remotes
        val config = """
            [s3-backup]
            type = s3
            provider = AWS
            region = us-east-1
            
            [webdav-server]
            type = webdav
            url = http://localhost:8080/dav
            
            [google-drive]
            type = drive
            scope = drive
        """.trimIndent()

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: All three remotes are found in order
        assertThat(remotes).hasSize(3)
        assertThat(remotes[0].name).isEqualTo("s3-backup")
        assertThat(remotes[0].type).isEqualTo("s3")
        assertThat(remotes[1].name).isEqualTo("webdav-server")
        assertThat(remotes[1].type).isEqualTo("webdav")
        assertThat(remotes[2].name).isEqualTo("google-drive")
        assertThat(remotes[2].type).isEqualTo("drive")
    }

    @Test
    fun `parseConfigContent handles config with comments`() {
        // Given: A config with comments
        val config = """
            # This is my primary backup
            [s3-backup]
            type = s3
            # AWS region
            provider = AWS
            
            # WebDAV server
            [webdav-server]
            type = webdav
            url = http://localhost:8080/dav
        """.trimIndent()

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: Comments are ignored, remotes are found
        assertThat(remotes).hasSize(2)
        assertThat(remotes[0].name).isEqualTo("s3-backup")
        assertThat(remotes[1].name).isEqualTo("webdav-server")
    }

    @Test
    fun `parseConfigContent handles empty config`() {
        // Given: An empty config
        val config = ""

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: No remotes are found
        assertThat(remotes).isEmpty()
    }

    @Test
    fun `parseConfigContent handles config with only comments`() {
        // Given: A config with only comments
        val config = """
            # This is a comment
            # Another comment
        """.trimIndent()

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: No remotes are found
        assertThat(remotes).isEmpty()
    }

    @Test
    fun `parseConfigContent handles config with blank lines`() {
        // Given: A config with extra blank lines
        val config = """
            
            
            [remote1]
            type = s3
            
            
            [remote2]
            type = webdav
            
            
        """.trimIndent()

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: Blank lines are ignored
        assertThat(remotes).hasSize(2)
    }

    @Test
    fun `parseConfigContent handles remote without type`() {
        // Given: A malformed config missing the type field
        val config = """
            [incomplete-remote]
            url = http://localhost:8080
        """.trimIndent()

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: Remote without type is not included (parser requires both name and type)
        // This is correct behavior - rclone remotes must have a type to be valid
        assertThat(remotes).isEmpty()
    }

    @Test
    fun `parseConfigContent handles remote names with special characters`() {
        // Given: Remote names with hyphens, underscores, and numbers
        val config = """
            [my-backup_2023]
            type = s3
            
            [test_server-01]
            type = webdav
        """.trimIndent()

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: Names with special characters are parsed correctly
        assertThat(remotes).hasSize(2)
        assertThat(remotes[0].name).isEqualTo("my-backup_2023")
        assertThat(remotes[1].name).isEqualTo("test_server-01")
    }

    @Test
    fun `parseConfigContent handles config with extra whitespace`() {
        // Given: A config with inconsistent whitespace around brackets and equals
        val config = """
            [  remote1  ]
            type   =   s3  
                provider = AWS
            
            [remote2]
               type=webdav
        """.trimIndent()

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: Whitespace is trimmed, but remote names contain the spaces
        // Parser trims line, but substring(1, length-1) keeps internal spaces
        assertThat(remotes).hasSize(2)
        assertThat(remotes[0].name).isEqualTo("  remote1  ") // Spaces preserved in brackets
        assertThat(remotes[0].type).isEqualTo("s3")
        assertThat(remotes[1].name).isEqualTo("remote2")
        assertThat(remotes[1].type).isEqualTo("webdav")
    }

    @Test
    fun `test fixture creates valid rclone config`() {
        // Given: A test fixture config
        val config = TestFixtures.createRcloneConfig(
            remoteName = "test-remote",
            remoteType = "s3",
            additionalParams = mapOf(
                "provider" to "AWS",
                "region" to "us-west-2"
            )
        )

        // When: Parsing the fixture config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: It parses correctly
        assertThat(remotes).hasSize(1)
        assertThat(remotes[0].name).isEqualTo("test-remote")
        assertThat(remotes[0].type).isEqualTo("s3")
    }

    @Test
    fun `parseConfigContent handles real-world complex config`() {
        // Given: A realistic complex rclone config
        val config = """
            # My AWS S3 backup
            [aws-s3]
            type = s3
            provider = AWS
            env_auth = false
            access_key_id = AKIAIOSFODNN7EXAMPLE
            secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
            region = us-east-1
            
            # WebDAV server
            [webdav-emulator]
            type = webdav
            url = http://10.0.2.2:8080/dav
            vendor = other
            user = admin
            pass = obscured_password_here
            
            # Google Drive
            [google-drive-photos]
            type = drive
            scope = drive.file
            root_folder_id = 
            
            # Dropbox
            [dropbox-backup]
            type = dropbox
            token = {"access_token":"token_here"}
        """.trimIndent()

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: All four remotes are found
        assertThat(remotes).hasSize(4)
        val remoteNames = remotes.map { it.name }
        assertThat(remoteNames).containsExactly(
            "aws-s3", 
            "webdav-emulator", 
            "google-drive-photos", 
            "dropbox-backup"
        ).inOrder()
    }

    @Test
    fun `parseConfigContent handles semicolon comments`() {
        // Given: A config with semicolon-style comments (alternative comment syntax)
        val config = """
            ; This is a semicolon comment
            [s3-remote]
            type = s3
            ; Another comment
            provider = AWS
        """.trimIndent()

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: Semicolon comments are ignored correctly
        assertThat(remotes).hasSize(1)
        assertThat(remotes[0].name).isEqualTo("s3-remote")
        assertThat(remotes[0].type).isEqualTo("s3")
    }

    @Test
    fun `parseConfigContent handles case-insensitive type keyword`() {
        // Given: A config with different capitalizations of "type"
        val config = """
            [remote1]
            TYPE = s3
            
            [remote2]
            Type = webdav
            
            [remote3]
            type = drive
        """.trimIndent()

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: All type variations are recognized (ignoreCase = true)
        assertThat(remotes).hasSize(3)
        assertThat(remotes[0].type).isEqualTo("s3")
        assertThat(remotes[1].type).isEqualTo("webdav")
        assertThat(remotes[2].type).isEqualTo("drive")
    }

    @Test
    fun `parseConfigContent handles type values with quotes`() {
        // Given: A config where type value has quotes
        val config = """
            [quoted-remote]
            type = "s3"
            provider = AWS
        """.trimIndent()

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: Quotes are included in the type (not stripped by parser)
        assertThat(remotes).hasSize(1)
        assertThat(remotes[0].name).isEqualTo("quoted-remote")
        assertThat(remotes[0].type).isEqualTo("\"s3\"")
    }

    @Test
    fun `parseConfigContent handles malformed bracket syntax`() {
        // Given: Configs with malformed brackets
        val configMissingClosing = """
            [incomplete-bracket
            type = s3
        """.trimIndent()

        val configMissingOpening = """
            incomplete-bracket]
            type = s3
        """.trimIndent()

        // When: Parsing malformed configs
        val remotes1 = RcloneConfigParser.parseConfigContent(configMissingClosing)
        val remotes2 = RcloneConfigParser.parseConfigContent(configMissingOpening)

        // Then: Malformed brackets are not recognized as remote sections
        assertThat(remotes1).isEmpty()
        assertThat(remotes2).isEmpty()
    }

    @Test
    fun `parseConfigContent handles empty remote name`() {
        // Given: A config with empty brackets
        val config = """
            []
            type = s3
        """.trimIndent()

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: Remote with empty name is created (parser allows it)
        assertThat(remotes).hasSize(1)
        assertThat(remotes[0].name).isEmpty()
        assertThat(remotes[0].type).isEqualTo("s3")
    }

    @Test
    fun `parseConfigContent handles multiple type declarations`() {
        // Given: A config where type is declared multiple times (last one wins)
        val config = """
            [remote]
            type = s3
            type = webdav
            type = drive
        """.trimIndent()

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: Last type value is used
        assertThat(remotes).hasSize(1)
        assertThat(remotes[0].type).isEqualTo("drive")
    }

    @Test
    fun `RcloneRemote toString returns formatted string`() {
        // Given: An RcloneRemote instance
        val remote = RcloneConfigParser.RcloneRemote(
            name = "my-backup",
            type = "s3"
        )

        // When: Calling toString()
        val result = remote.toString()

        // Then: Returns formatted string with name and type
        assertThat(result).isEqualTo("my-backup (s3)")
    }

    @Test
    fun `RcloneRemote displayName defaults to name`() {
        // Given: An RcloneRemote without explicit displayName
        val remote = RcloneConfigParser.RcloneRemote(
            name = "my-backup",
            type = "s3"
        )

        // When: Accessing displayName
        val displayName = remote.displayName

        // Then: It equals the name
        assertThat(displayName).isEqualTo("my-backup")
    }

    @Test
    fun `RcloneRemote displayName can be customized`() {
        // Given: An RcloneRemote with custom displayName
        val remote = RcloneConfigParser.RcloneRemote(
            name = "my-backup",
            type = "s3",
            displayName = "My Awesome Backup"
        )

        // When: Accessing displayName
        val displayName = remote.displayName

        // Then: It returns the custom value
        assertThat(displayName).isEqualTo("My Awesome Backup")
        assertThat(remote.name).isEqualTo("my-backup") // name unchanged
    }

    @Test
    fun `parseConfigContent handles config ending without newline`() {
        // Given: A config string without trailing newline after last remote
        val config = "[remote]\ntype = s3"

        // When: Parsing the config
        val remotes = RcloneConfigParser.parseConfigContent(config)

        // Then: Last remote is parsed correctly (handled by final block in parser)
        assertThat(remotes).hasSize(1)
        assertThat(remotes[0].name).isEqualTo("remote")
        assertThat(remotes[0].type).isEqualTo("s3")
    }

    // File-based parsing tests

    @Test
    fun `parseConfig parses valid config file correctly`() {
        // Given: A temporary config file with valid content
        val configFile = tempFolder.newFile("rclone.conf")
        configFile.writeText("""
            [s3-backup]
            type = s3
            provider = AWS
            
            [webdav-server]
            type = webdav
            url = http://localhost:8080
        """.trimIndent())

        // When: Parsing the file
        val remotes = RcloneConfigParser.parseConfig(configFile)

        // Then: Both remotes are found
        assertThat(remotes).hasSize(2)
        assertThat(remotes[0].name).isEqualTo("s3-backup")
        assertThat(remotes[0].type).isEqualTo("s3")
        assertThat(remotes[1].name).isEqualTo("webdav-server")
        assertThat(remotes[1].type).isEqualTo("webdav")
    }

    @Test
    fun `parseConfig returns empty list for non-existent file`() {
        // Given: A file that doesn't exist
        val nonExistentFile = File(tempFolder.root, "does-not-exist.conf")

        // When: Attempting to parse
        val remotes = RcloneConfigParser.parseConfig(nonExistentFile)

        // Then: Returns empty list (doesn't throw exception)
        assertThat(remotes).isEmpty()
    }

    @Test
    fun `parseConfig returns empty list for unreadable file`() {
        // Given: A file that exists but isn't readable
        val configFile = tempFolder.newFile("unreadable.conf")
        configFile.writeText("[remote]\ntype = s3")
        configFile.setReadable(false)

        try {
            // When: Attempting to parse
            val remotes = RcloneConfigParser.parseConfig(configFile)

            // Then: Returns empty list (graceful handling)
            assertThat(remotes).isEmpty()
        } finally {
            // Cleanup: Restore permissions
            configFile.setReadable(true)
        }
    }

    @Test
    fun `parseConfig handles empty file`() {
        // Given: An empty config file
        val configFile = tempFolder.newFile("empty.conf")
        // File is empty by default

        // When: Parsing the empty file
        val remotes = RcloneConfigParser.parseConfig(configFile)

        // Then: Returns empty list
        assertThat(remotes).isEmpty()
    }

    @Test
    fun `isValidConfig returns true for file with remotes`() {
        // Given: A config file with valid remotes
        val configFile = tempFolder.newFile("valid.conf")
        configFile.writeText("""
            [my-remote]
            type = s3
        """.trimIndent())

        // When: Validating the file
        val isValid = RcloneConfigParser.isValidConfig(configFile)

        // Then: Returns true
        assertThat(isValid).isTrue()
    }

    @Test
    fun `isValidConfig returns false for file without remotes`() {
        // Given: A config file with no valid remotes
        val configFile = tempFolder.newFile("invalid.conf")
        configFile.writeText("""
            # Just comments
            # No remotes here
        """.trimIndent())

        // When: Validating the file
        val isValid = RcloneConfigParser.isValidConfig(configFile)

        // Then: Returns false
        assertThat(isValid).isFalse()
    }

    @Test
    fun `isValidConfig returns false for non-existent file`() {
        // Given: A file that doesn't exist
        val nonExistentFile = File(tempFolder.root, "missing.conf")

        // When: Validating the file
        val isValid = RcloneConfigParser.isValidConfig(nonExistentFile)

        // Then: Returns false (not valid)
        assertThat(isValid).isFalse()
    }

    @Test
    fun `isValidConfig returns false for unreadable file`() {
        // Given: A file that isn't readable
        val configFile = tempFolder.newFile("unreadable.conf")
        configFile.writeText("[remote]\ntype = s3")
        configFile.setReadable(false)

        try {
            // When: Validating the file
            val isValid = RcloneConfigParser.isValidConfig(configFile)

            // Then: Returns false (can't read = not valid)
            assertThat(isValid).isFalse()
        } finally {
            // Cleanup: Restore permissions
            configFile.setReadable(true)
        }
    }

    @Test
    fun `isValidConfig returns false for file with incomplete remotes`() {
        // Given: A config file with remotes but no type fields
        val configFile = tempFolder.newFile("incomplete.conf")
        configFile.writeText("""
            [remote1]
            url = http://localhost
            
            [remote2]
            provider = AWS
        """.trimIndent())

        // When: Validating the file
        val isValid = RcloneConfigParser.isValidConfig(configFile)

        // Then: Returns false (no valid remotes = invalid)
        assertThat(isValid).isFalse()
    }
}

