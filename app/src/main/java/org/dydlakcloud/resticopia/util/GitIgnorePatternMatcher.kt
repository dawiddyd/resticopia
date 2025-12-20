package org.dydlakcloud.resticopia.util

import java.io.File
import java.util.regex.Pattern

/**
 * GitIgnore-style pattern matcher for file exclusion.
 * 
 * Supports standard .gitignore syntax:
 * - Wildcards: * (any characters), ? (single character)
 * - Directory matching: trailing slash / indicates directory
 * - Negation: ! prefix to negate a pattern
 * - Comments: # prefix for comments
 * - Anchored patterns: / prefix anchors to root
 * 
 * Example patterns:
 * - *.log - matches all .log files in any directory
 * - temp/ - matches any directory named "temp"
 * - /build - matches "build" only at root
 * - !important.log - negates previous exclusions
 */
object GitIgnorePatternMatcher {
    
    /**
     * Compiled pattern with metadata
     */
    private data class CompiledPattern(
        val pattern: Pattern,
        val isNegation: Boolean,
        val isDirectory: Boolean,
        val isAnchored: Boolean
    )
    
    /**
     * Parse ignore patterns from string content.
     * Each line is treated as a separate pattern.
     * 
     * @param content The patterns content (similar to .gitignore file)
     * @return List of parsed patterns
     */
    fun parsePatterns(content: String): List<String> {
        return content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    }
    
    /**
     * Compile patterns for efficient matching.
     * 
     * @param patterns List of pattern strings
     * @return List of compiled patterns
     */
    private fun compilePatterns(patterns: List<String>): List<CompiledPattern> {
        return patterns.mapNotNull { pattern ->
            try {
                var pat = pattern.trim()
                if (pat.isEmpty() || pat.startsWith("#")) {
                    return@mapNotNull null
                }
                
                // Check for negation
                val isNegation = pat.startsWith("!")
                if (isNegation) {
                    pat = pat.substring(1).trim()
                }
                
                // Check for directory-only pattern
                val isDirectory = pat.endsWith("/")
                if (isDirectory) {
                    pat = pat.substring(0, pat.length - 1)
                }
                
                // Check if pattern is anchored to root
                val isAnchored = pat.startsWith("/")
                if (isAnchored) {
                    pat = pat.substring(1)
                }
                
                // Convert gitignore pattern to regex
                val regex = patternToRegex(pat)
                
                CompiledPattern(
                    pattern = Pattern.compile(regex),
                    isNegation = isNegation,
                    isDirectory = isDirectory,
                    isAnchored = isAnchored
                )
            } catch (e: Exception) {
                // Skip invalid patterns
                null
            }
        }
    }
    
    /**
     * Convert gitignore glob pattern to regex pattern.
     * 
     * @param pattern The glob pattern
     * @return Regex pattern string
     */
    private fun patternToRegex(pattern: String): String {
        val regex = StringBuilder()
        var i = 0
        
        while (i < pattern.length) {
            val c = pattern[i]
            when (c) {
                '*' -> {
                    // Check for ** (match any directories)
                    if (i + 1 < pattern.length && pattern[i + 1] == '*') {
                        // Check if followed by /
                        if (i + 2 < pattern.length && pattern[i + 2] == '/') {
                            // **/ matches zero or more directories
                            regex.append("(.*/)*")
                            i += 2 // Skip both * and /
                        } else {
                            // ** not followed by /, treat as **
                            regex.append(".*")
                            i++
                        }
                    } else {
                        // Single * matches anything except /
                        regex.append("[^/]*")
                    }
                }
                '?' -> regex.append("[^/]")
                '.' -> regex.append("\\.")
                '+' -> regex.append("\\+")
                '(' -> regex.append("\\(")
                ')' -> regex.append("\\)")
                '[' -> regex.append("\\[")
                ']' -> regex.append("\\]")
                '{' -> regex.append("\\{")
                '}' -> regex.append("\\}")
                '^' -> regex.append("\\^")
                '$' -> regex.append("\\$")
                '|' -> regex.append("\\|")
                '\\' -> {
                    // Escape next character
                    if (i + 1 < pattern.length) {
                        i++
                        regex.append(Pattern.quote(pattern[i].toString()))
                    }
                }
                else -> regex.append(Pattern.quote(c.toString()))
            }
            i++
        }
        
        return regex.toString()
    }
    
    /**
     * Check if a file should be excluded based on patterns.
     * 
     * @param file The file to check
     * @param rootPath The root path for backup (for calculating relative paths)
     * @param patterns The ignore patterns (as string content)
     * @return true if the file should be excluded, false otherwise
     */
    fun shouldExclude(file: File, rootPath: File, patterns: String): Boolean {
        if (patterns.isEmpty()) {
            return false
        }
        
        val patternList = parsePatterns(patterns)
        return shouldExclude(file, rootPath, patternList)
    }
    
    /**
     * Check if a file should be excluded based on parsed patterns.
     * 
     * @param file The file to check
     * @param rootPath The root path for backup (for calculating relative paths)
     * @param patternList List of pattern strings
     * @return true if the file should be excluded, false otherwise
     */
    fun shouldExclude(file: File, rootPath: File, patternList: List<String>): Boolean {
        if (patternList.isEmpty()) {
            return false
        }
        
        val compiledPatterns = compilePatterns(patternList)
        val relativePath = getRelativePath(rootPath, file)
        val isDirectory = file.isDirectory
        
        var excluded = false
        
        // Process patterns in order (last matching pattern wins)
        for (compiledPattern in compiledPatterns) {
            // Skip directory-only patterns for files
            if (compiledPattern.isDirectory && !isDirectory) {
                continue
            }
            
            val matches = if (compiledPattern.isAnchored) {
                // Anchored pattern: match from root
                compiledPattern.pattern.matcher(relativePath).matches()
            } else {
                // Non-anchored: match against any path component
                matchesAnyComponent(relativePath, compiledPattern.pattern)
            }
            
            if (matches) {
                // Negation patterns include the file, others exclude it
                excluded = !compiledPattern.isNegation
            }
        }
        
        return excluded
    }
    
    /**
     * Calculate relative path from root to file.
     * 
     * @param root The root directory
     * @param file The file
     * @return Relative path string
     */
    private fun getRelativePath(root: File, file: File): String {
        val rootPath = root.canonicalPath
        val filePath = file.canonicalPath
        
        return if (filePath.startsWith(rootPath)) {
            filePath.substring(rootPath.length)
                .removePrefix("/")
                .removePrefix("\\")
        } else {
            filePath
        }
    }
    
    /**
     * Check if pattern matches any path component.
     * For non-anchored patterns, we need to check if the pattern matches
     * the filename or any part of the path.
     * 
     * @param path The relative path
     * @param pattern The regex pattern
     * @return true if matches
     */
    private fun matchesAnyComponent(path: String, pattern: Pattern): Boolean {
        // Check full path
        if (pattern.matcher(path).matches()) {
            return true
        }
        
        // Check each component
        val components = path.split("/", "\\")
        for (component in components) {
            if (pattern.matcher(component).matches()) {
                return true
            }
        }
        
        // Check if pattern matches any suffix of the path
        val parts = path.split("/", "\\").filter { it.isNotEmpty() }
        for (i in parts.indices) {
            val subPath = parts.subList(i, parts.size).joinToString("/")
            if (pattern.matcher(subPath).matches()) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Filter a list of files based on ignore patterns.
     * 
     * @param files List of files to filter
     * @param rootPath The root path for backup
     * @param patterns The ignore patterns
     * @return Filtered list of files (excluded files removed)
     */
    fun filterFiles(files: List<File>, rootPath: File, patterns: String): List<File> {
        if (patterns.isEmpty()) {
            return files
        }
        
        return files.filter { file ->
            !shouldExclude(file, rootPath, patterns)
        }
    }
}

