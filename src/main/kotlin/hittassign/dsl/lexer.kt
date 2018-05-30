package hittassign.dsl

import com.github.kittinunf.result.Result

/**
 * Language lexemes ADT
 */
sealed class HitLexeme {
    object Ident : HitLexeme()

    object Dedent : HitLexeme()

    data class Symbol(val sym: String) : HitLexeme() // symbol or string literal
}

object LexError : Exception()

/**
 * State machine to read string into [HitLexeme] list, char by char
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
     * Reading line ident sate
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
                    else -> Failure // Invalid ident
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
                else -> WaitingSymbol(ident, acc.copy(tokens = acc.tokens + HitLexeme.Symbol(sym))).read(c)
            }
        }

        override fun finish(): LexReader = acc.copy(tokens = acc.tokens + HitLexeme.Symbol(sym)).finish()
    }

    /**
     * Reading failure state
     * TODO: add error details
     */
    object Failure : LexReader() {
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


        private fun isLineBreak(c: Char) = c == '\r' || c == '\n'
    }
}

/**
 * Parses input string [str] into [HitLexeme] list
 */
fun lex(str: String): Result<List<HitLexeme>, LexError> {
    val acc = str.fold<LexReader>(LexReader.Empty, { acc, c -> acc.read(c) }).finish()

    return when (acc) {
        is LexReader.Success -> Result.Success(acc.tokens)
        else -> Result.Failure(LexError) // FIXME add detailed error message
    }
}
