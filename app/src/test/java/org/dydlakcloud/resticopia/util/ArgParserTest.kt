package org.dydlakcloud.resticopia.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ArgParserTest {
    @Test
    fun `test basic argument parsing`() {
        val argStr = """abc def gh\ ijk "lmn op" 'qr st'"""
        val args = listOf("abc", "def", "gh ijk", "lmn op", "qr st")

        assertEquals(null, ArgsParser.validate(argStr))
        assertEquals(args, ArgsParser.parse(argStr))
    }

    @Test
    fun `test argument parsing with quote literals`() {
        val argStr = """\"abc def\" \'gh ijk\' "'lmn'" '"op"'"""
        val args = listOf("\"abc", "def\"", "'gh", "ijk'", "'lmn'", "\"op\"")

        assertEquals(null, ArgsParser.validate(argStr))
        assertEquals(args, ArgsParser.parse(argStr))
    }

    @Test
    fun `test argument parsing with escaped backslash`() {
        val argStr = """abc\\def "gh\\ijk" 'lmn\op'"""
        val args = listOf("abc\\def", "gh\\ijk", "lmn\\op")

        assertEquals(null, ArgsParser.validate(argStr))
        assertEquals(args, ArgsParser.parse(argStr))
    }

    @Test
    fun `test argument parsing with quote concatenation`() {
        val argStr = """"abc""def" 'gh''ijk' "lmn"'"'"op" 'qr'"'"'st'"""
        val args = listOf("abcdef", "ghijk", "lmn\"op", "qr'st")

        assertEquals(null, ArgsParser.validate(argStr))
        assertEquals(args, ArgsParser.parse(argStr))
    }

    @Test
    fun `test argument parsing with extra spaces`() {
        val argStr = """  abc  def   """
        val args = listOf("abc", "def")

        assertEquals(null, ArgsParser.validate(argStr))
        assertEquals(args, ArgsParser.parse(argStr))
    }

    @Test
    fun `test argument parsing trailing backslash`() {
        val argStr = """abc\"""

        assertEquals(ArgsParser.Error.BACKSLASH, ArgsParser.validate(argStr))
    }

    @Test
    fun `test argument parsing unterminated single quote`() {
        val argStr = """'abc"""

        assertEquals(ArgsParser.Error.SINGLE_QUOTE, ArgsParser.validate(argStr))
    }

    @Test
    fun `test argument parsing unterminated double quote`() {
        val argStr = """"abc"""

        assertEquals(ArgsParser.Error.DOUBLE_QUOTE, ArgsParser.validate(argStr))
    }
}