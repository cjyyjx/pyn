package com.pyn.editor

import android.graphics.Color
import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan

data class HighlightToken(
    val start: Int,
    val end: Int,
    val color: Int
)

object PythonSyntaxColors {
    val KEYWORD = Color.parseColor("#569cd6")
    val BUILTIN = Color.parseColor("#dcdcaa")
    val STRING = Color.parseColor("#ce9178")
    val COMMENT = Color.parseColor("#6a9955")
    val NUMBER = Color.parseColor("#b5cea8")
    val DECORATOR = Color.parseColor("#dcdcaa")
    val FUNCTION_DEF = Color.parseColor("#dcdcaa")
    val CLASS_DEF = Color.parseColor("#4ec9b0")
    val SELF = Color.parseColor("#569cd6")
    val OPERATOR = Color.parseColor("#d4d4d4")
    val NORMAL = Color.parseColor("#d4d4d4")
    val FSTRING = Color.parseColor("#ce9178")
    val IMPORT = Color.parseColor("#c586c0")
}

object PythonSyntaxHighlighter {

    private val KEYWORDS = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await",
        "break", "class", "continue", "def", "del", "elif", "else", "except",
        "finally", "for", "from", "global", "if", "import", "in", "is",
        "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
        "try", "while", "with", "yield"
    )

    private val BUILTINS = setOf(
        "abs", "all", "any", "bin", "bool", "bytearray", "bytes", "callable",
        "chr", "classmethod", "compile", "complex", "delattr", "dict", "dir",
        "divmod", "enumerate", "eval", "exec", "filter", "float", "format",
        "frozenset", "getattr", "globals", "hasattr", "hash", "help", "hex",
        "id", "input", "int", "isinstance", "issubclass", "iter", "len",
        "list", "locals", "map", "max", "memoryview", "min", "next", "object",
        "oct", "open", "ord", "pow", "print", "property", "range", "repr",
        "reversed", "round", "set", "setattr", "slice", "sorted", "staticmethod",
        "str", "sum", "super", "tuple", "type", "vars", "zip", "__import__",
        "Exception", "BaseException", "ValueError", "TypeError", "KeyError",
        "IndexError", "RuntimeError", "StopIteration", "AttributeError",
        "ImportError", "ModuleNotFoundError", "FileNotFoundError", "OSError",
        "IOError", "ZeroDivisionError", "NameError", "SyntaxError", "IndentationError"
    )

    private val IMPORT_KEYWORDS = setOf("import", "from", "as")

    private val KEYWORD_COLORS: Map<String, Int> = mapOf(
        "def" to PythonSyntaxColors.FUNCTION_DEF,
        "class" to PythonSyntaxColors.CLASS_DEF,
        "import" to PythonSyntaxColors.IMPORT,
        "from" to PythonSyntaxColors.IMPORT,
        "as" to PythonSyntaxColors.IMPORT,
    )

    fun highlight(text: String): List<HighlightToken> {
        val tokens = mutableListOf<HighlightToken>()
        val len = text.length
        var i = 0

        while (i < len) {
            val ch = text[i]

            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                i++
                continue
            }

            if (ch == '#') {
                val start = i
                while (i < len && text[i] != '\n') i++
                tokens.add(HighlightToken(start, i, PythonSyntaxColors.COMMENT))
                continue
            }

            if (ch == '"' || ch == '\'') {
                val triple = (i + 2 < len && text[i] == text[i + 1] && text[i] == text[i + 2])
                val isFString = i > 0 && (text[i - 1] == 'f' || text[i - 1] == 'F' || text[i - 1] == 'r' || text[i - 1] == 'b' || text[i - 1] == 'u')
                val color = if (isFString) PythonSyntaxColors.FSTRING else PythonSyntaxColors.STRING

                if (triple) {
                    val quote = text.substring(i, i + 3)
                    val start = i
                    i += 3
                    while (i < len) {
                        if (i + 2 < len && text.substring(i, i + 3) == quote) {
                            i += 3
                            break
                        }
                        if (text[i] == '\\') i += 2 else i++
                    }
                    tokens.add(HighlightToken(start, i, color))
                } else {
                    val quote = ch
                    val start = i
                    i++
                    while (i < len) {
                        if (text[i] == '\\') i += 2
                        else if (text[i] == quote) { i++; break }
                        else i++
                    }
                    tokens.add(HighlightToken(start, i, color))
                }
                continue
            }

            if (ch == '@') {
                val start = i
                i++
                while (i < len && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '.')) i++
                tokens.add(HighlightToken(start, i, PythonSyntaxColors.DECORATOR))
                continue
            }

            if (ch == '_' || ch.isLetter()) {
                val start = i
                while (i < len && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                val word = text.substring(start, i)
                when {
                    word in KEYWORDS -> {
                        val color = KEYWORD_COLORS[word] ?: PythonSyntaxColors.KEYWORD
                        tokens.add(HighlightToken(start, i, color))
                    }
                    word == "self" || word == "cls" -> {
                        tokens.add(HighlightToken(start, i, PythonSyntaxColors.SELF))
                    }
                    word in BUILTINS -> {
                        tokens.add(HighlightToken(start, i, PythonSyntaxColors.BUILTIN))
                    }
                }
                continue
            }

            if (ch == '.' && i + 1 < len && (text[i + 1].isLetter() || text[i + 1] == '_')) {
                tokens.add(HighlightToken(i, i + 1, PythonSyntaxColors.OPERATOR))
                i++
                continue
            }

            if (ch.isDigit() || (ch == '.' && i + 1 < len && text[i + 1].isDigit())) {
                val start = i
                if (ch == '0' && i + 1 < len && (text[i + 1] == 'x' || text[i + 1] == 'X')) {
                    i += 2
                    while (i < len && (text[i].isDigit() || "abcdefABCDEF".contains(text[i]))) i++
                } else if (ch == '0' && i + 1 < len && (text[i + 1] == 'b' || text[i + 1] == 'B')) {
                    i += 2
                    while (i < len && (text[i] == '0' || text[i] == '1')) i++
                } else {
                    while (i < len && text[i].isDigit()) i++
                    if (i < len && text[i] == '.') {
                        i++
                        while (i < len && text[i].isDigit()) i++
                    }
                    if (i < len && (text[i] == 'e' || text[i] == 'E')) {
                        i++
                        if (i < len && (text[i] == '+' || text[i] == '-')) i++
                        while (i < len && text[i].isDigit()) i++
                    }
                }
                tokens.add(HighlightToken(start, i, PythonSyntaxColors.NUMBER))
                continue
            }

            i++
        }

        return tokens
    }

    fun applyHighlighting(editable: Editable, text: String) {
        val spans = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
        for (span in spans) {
            editable.removeSpan(span)
        }

        val tokens = highlight(text)
        for (token in tokens) {
            if (token.start < editable.length && token.end <= editable.length) {
                editable.setSpan(
                    ForegroundColorSpan(token.color),
                    token.start, token.end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }
}
