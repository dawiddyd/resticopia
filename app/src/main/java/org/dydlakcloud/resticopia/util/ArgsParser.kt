package org.dydlakcloud.resticopia.util

/**
 * Utility object for argument tokenizing
 */
object ArgsParser {
    /**
     * Basic parser for turning a string into an array of command-line arguments.
     * Handles quoted arguments, and backslash for space and quote literals.
     */
    fun parse(argStr: String): List<String> {
        val args = mutableListOf<String>()

        val curArg = StringBuilder()
        var escNext = false
        var quote = '\u0000'

        argStr.forEach { c ->
            if (escNext) {
                curArg.append(c)
                escNext = false
            } else if (quote != '\u0000' && c != quote) {
                if (quote != '\'' && c == '\\') escNext = true
                else curArg.append(c)
            } else when (c) {
                ' ' -> {
                    if (curArg.isNotEmpty()) {
                        args.add(curArg.toString())
                        curArg.clear()
                    }
                }
                '"', '\'' -> quote = if (quote == '\u0000') c else '\u0000'
                '\\' -> escNext = true
                else -> curArg.append(c)
            }
        }

        // Flush last argument
        if (curArg.isNotEmpty()) {
            args.add(curArg.toString())
        }

        return args
    }

    enum class Error {
        BACKSLASH, SINGLE_QUOTE, DOUBLE_QUOTE
    }

    /**
     * Checks for possible parsing issues given an arguments string.
     * Checks if the string ends with an unescaped backslash or an unterminated quotation.
     * Note: parse() will silently ignore these issues and return the arguments
     * without any unescaped backslashes, and assuming a terminating quote.
     */
    fun validate(argStr: String): Error? {
        var escNext = false
        var quote = '\u0000'

        argStr.forEach { c ->
            if (escNext) {
                escNext = false
            } else if (quote != '\u0000' && c != quote) {
                if (quote != '\'' && c == '\\') escNext = true
            } else when (c) {
                '"', '\'' -> quote = if (quote == '\u0000') c else '\u0000'
                '\\' -> escNext = true
            }
        }

        if (escNext) return Error.BACKSLASH
        if (quote == '\'') return Error.SINGLE_QUOTE
        if (quote != '\u0000') return Error.DOUBLE_QUOTE
        return null
    }
}
