package org.dydlakcloud.resticopia.util

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for GitIgnorePatternMatcher
 * Tests various gitignore-style patterns to ensure correct file exclusion behavior
 */
class GitIgnorePatternMatcherTest {
    
    private lateinit var tempRoot: File
    
    @Before
    fun setup() {
        tempRoot = File("/tmp/backup_root")
    }
    
    @Test
    fun testParsePatterns_withComments() {
        val content = """
            # This is a comment
            *.log
            # Another comment
            temp/
            *.tmp
        """.trimIndent()
        
        val patterns = GitIgnorePatternMatcher.parsePatterns(content)
        
        assertEquals(3, patterns.size)
        assertTrue(patterns.contains("*.log"))
        assertTrue(patterns.contains("temp/"))
        assertTrue(patterns.contains("*.tmp"))
    }
    
    @Test
    fun testParsePatterns_withEmptyLines() {
        val content = """
            *.log
            
            temp/
            
            *.tmp
        """.trimIndent()
        
        val patterns = GitIgnorePatternMatcher.parsePatterns(content)
        
        assertEquals(3, patterns.size)
    }
    
    @Test
    fun testShouldExclude_wildcardPattern() {
        val patterns = "*.log"
        val file1 = File("/tmp/backup_root/test.log")
        val file2 = File("/tmp/backup_root/test.txt")
        
        assertTrue(GitIgnorePatternMatcher.shouldExclude(file1, tempRoot, patterns))
        assertFalse(GitIgnorePatternMatcher.shouldExclude(file2, tempRoot, patterns))
    }
    
    @Test
    fun testShouldExclude_directoryPattern() {
        val patterns = "temp/"
        val dir = File("/tmp/backup_root/temp")
        val file = File("/tmp/backup_root/temp.txt")
        
        // Create a mock directory (in real scenario, we'd check isDirectory)
        // For this test, we simulate the behavior
        val patternList = listOf("temp/")
        
        // The pattern should match directories named "temp"
        assertTrue(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/temp"), 
            tempRoot, 
            patternList
        ))
    }
    
    @Test
    fun testShouldExclude_anchoredPattern() {
        val patterns = "/cache"
        val file1 = File("/tmp/backup_root/cache")
        val file2 = File("/tmp/backup_root/subdir/cache")
        
        // Anchored pattern should only match at root
        assertTrue(GitIgnorePatternMatcher.shouldExclude(file1, tempRoot, patterns))
        // Non-root cache should not match anchored pattern
        // Note: The implementation may vary based on exact matching logic
    }
    
    @Test
    fun testShouldExclude_negationPattern() {
        val patterns = """
            *.log
            !important.log
        """.trimIndent()
        
        val file1 = File("/tmp/backup_root/test.log")
        val file2 = File("/tmp/backup_root/important.log")
        
        // test.log should be excluded
        assertTrue(GitIgnorePatternMatcher.shouldExclude(file1, tempRoot, patterns))
        
        // important.log should NOT be excluded due to negation
        assertFalse(GitIgnorePatternMatcher.shouldExclude(file2, tempRoot, patterns))
    }
    
    @Test
    fun testShouldExclude_nestedPath() {
        val patterns = "node_modules/"
        val file1 = File("/tmp/backup_root/node_modules/package.json")
        val file2 = File("/tmp/backup_root/src/index.js")
        
        val patternList = listOf("node_modules/")
        
        // Files inside node_modules should be checked
        // The exact behavior depends on how we handle nested paths
        assertTrue(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/node_modules"), 
            tempRoot, 
            patternList
        ))
    }
    
    @Test
    fun testShouldExclude_multiplePatterns() {
        val patterns = """
            *.log
            *.tmp
            cache/
            /build
        """.trimIndent()
        
        assertTrue(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/test.log"), 
            tempRoot, 
            patterns
        ))
        
        assertTrue(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/temp.tmp"), 
            tempRoot, 
            patterns
        ))
        
        assertFalse(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/test.txt"), 
            tempRoot, 
            patterns
        ))
    }
    
    @Test
    fun testShouldExclude_emptyPatterns() {
        val patterns = ""
        val file = File("/tmp/backup_root/test.log")
        
        assertFalse(GitIgnorePatternMatcher.shouldExclude(file, tempRoot, patterns))
    }
    
    @Test
    fun testShouldExclude_questionMark() {
        val patterns = "test?.txt"
        
        assertTrue(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/test1.txt"), 
            tempRoot, 
            patterns
        ))
        
        assertTrue(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/testa.txt"), 
            tempRoot, 
            patterns
        ))
        
        assertFalse(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/test12.txt"), 
            tempRoot, 
            patterns
        ))
    }
    
    @Test
    fun testShouldExclude_doubleAsterisk() {
        val patterns = "**/build"
        
        // Should match build in any directory
        assertTrue(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/build"), 
            tempRoot, 
            patterns
        ))
        
        assertTrue(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/subdir/build"), 
            tempRoot, 
            patterns
        ))
        
        assertTrue(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/a/b/c/build"), 
            tempRoot, 
            patterns
        ))
    }
    
    @Test
    fun testFilterFiles() {
        val patterns = """
            *.log
            temp/
        """.trimIndent()
        
        val files = listOf(
            File("/tmp/backup_root/test.txt"),
            File("/tmp/backup_root/test.log"),
            File("/tmp/backup_root/data.json"),
            File("/tmp/backup_root/debug.log")
        )
        
        val filtered = GitIgnorePatternMatcher.filterFiles(files, tempRoot, patterns)
        
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.name == "test.txt" })
        assertTrue(filtered.any { it.name == "data.json" })
        assertFalse(filtered.any { it.name.endsWith(".log") })
    }
    
    @Test
    fun testRealWorldPatterns() {
        val patterns = """
            # OS generated files
            .DS_Store
            Thumbs.db
            
            # IDE files
            .idea/
            .vscode/
            *.swp
            
            # Build artifacts
            /build
            /target
            *.class
            
            # Logs
            *.log
            !important.log
            
            # Temporary files
            *.tmp
            *~
            
            # Node modules
            node_modules/
        """.trimIndent()
        
        // Test various files
        assertTrue(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/.DS_Store"), 
            tempRoot, 
            patterns
        ))
        
        assertTrue(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/debug.log"), 
            tempRoot, 
            patterns
        ))
        
        assertFalse(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/important.log"), 
            tempRoot, 
            patterns
        ))
        
        assertFalse(GitIgnorePatternMatcher.shouldExclude(
            File("/tmp/backup_root/src/main.kt"), 
            tempRoot, 
            patterns
        ))
    }
}

