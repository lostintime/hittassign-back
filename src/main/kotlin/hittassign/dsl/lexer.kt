package hittassign.dsl

import com.github.kittinunf.result.Result

/**
 * Language lexemes ADT
 */
sealed class HitLexeme {
    /**
     * Ident token means child block start (like '{')
     */
    object Ident : HitLexeme()

    /**
     * Dedent token means child block end (like '}')
     */
    object Dedent : HitLexeme()

    /**
     * Symbol token is non whitespace string
     */
    data class Symbol(val sym: String) : HitLexeme() // symbol or string literal
}

sealed class LexError : Exception() {
    /**
     * Invalid ident found in source code
     */
    object InvalidIdent : LexError()

    /**
     * Parser finished with invalid sate
     */
    object InvalidFinalState : LexError()
}

/**
 * Lexer state machine: parses a [HitLexeme] list from a [Char] stream
 */
sealed class LexReader {
    /**
     * Read one more character
     */
    abstract fun read(c: Char): LexReader

    /**
     * Finish reading (EOF)
     */
    abstract fun finish(): LexReader

    /**
     * Success state: valid [HitLexeme] tokens read until now
     */
    data class Success(val tokens: List<HitLexeme>, val ident: Int) : LexReader() {
        override fun read(c: Char): LexReader = ReadingIdent(0, this).read(c)

        fun addIdent(): Success = Success(tokens + HitLexeme.Ident, ident + IDENT_SIZE)

        fun addDedent(): Success = Success(tokens + HitLexeme.Dedent, ident - IDENT_SIZE)

        override fun finish(): LexReader = Success(
            tokens + (ident downTo IDENT_SIZE step IDENT_SIZE).map { HitLexeme.Dedent },
            0
        )
    }

    /**
     * Reading line ident
     */
    data class ReadingIdent(private val ident: Int, private val acc: Success) :
        LexReader() {
        override fun read(c: Char): LexReader {
            return when (c) {
                ' ' -> ReadingIdent(ident + 1, acc)
                '\t' -> ReadingIdent(ident + IDENT_SIZE, acc)
                else -> WaitingSymbol(ident, acc).read(c)
            }
        }

        override fun finish(): LexReader = acc.finish()
    }

    /**
     * Waiting for [HitLexeme.Symbol] valid character state (after ident or previous symbol read)
     */
    data class WaitingSymbol(private val ident: Int, private val acc: Success) : LexReader() {
        override fun read(c: Char): LexReader {
            return when {
                isLineBreak(c) -> acc // end of line, done
                isSymbolChar(c) -> when {
                // same ident
                    ident == acc.ident -> ReadingSymbol(Character.toString(c), ident, acc)
                // add ident
                    ident == (acc.ident + IDENT_SIZE) -> WaitingSymbol(ident, acc.addIdent()).read(c)
                // add dedent
                    ident < acc.ident -> WaitingSymbol(ident, acc.addDedent()).read(c)
                    else -> Failure(LexError.InvalidIdent) // Invalid ident
                }
                else -> this // ignore char, waiting symbol
            }
        }

        override fun finish(): LexReader = acc.finish()
    }

    /**
     * Reading [HitLexeme.Symbol] in progress
     */
    data class ReadingSymbol(private val sym: String, private val ident: Int, private val acc: Success) : LexReader() {
        override fun read(c: Char): LexReader {
            return when {
            // continue reading symbol
                isSymbolChar(c) -> ReadingSymbol(sym + c, ident, acc)
            // finished reading symbol, waiting for next
                else -> if (sym.lastOrNull() == '\\') {
                    return ReadingSymbol(sym.dropLast(1) + c, ident, acc)
                } else {
                    WaitingSymbol(ident, acc.copy(tokens = acc.tokens + HitLexeme.Symbol(sym))).read(c)
                }
            }
        }

        override fun finish(): LexReader = acc.copy(tokens = acc.tokens + HitLexeme.Symbol(sym)).finish()
    }

    /**
     * Failure state, ignores all inputs
     */
    data class Failure(val err: LexError) : LexReader() {
        override fun read(c: Char): LexReader = this

        override fun finish(): LexReader = this
    }

    companion object {
        val Empty = Success(emptyList(), 0)

        private const val IDENT_SIZE = 2

        private fun isSymbolChar(c: Char): Boolean = when (c) {
            ' ' -> false
            '\t' -> false
            else -> !isLineBreak(c)
        }

        private fun isLineBreak(c: Char) = c == '\n'
    }
}

/**
 * Parses input string [str] into [HitLexeme] list
 */
fun lex(str: String): Result<List<HitLexeme>, LexError> {
    val acc = str.fold<LexReader>(LexReader.Empty, { acc, c -> acc.read(c) }).finish()

    return when (acc) {
        is LexReader.Success -> Result.Success(acc.tokens)
        is LexReader.Failure -> Result.Failure(acc.err)
        else -> Result.Failure(LexError.InvalidFinalState)
    }
}
