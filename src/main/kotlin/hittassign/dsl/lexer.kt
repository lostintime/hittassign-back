package hittassign.dsl

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.map

/**
 * Source file position reference
 */
data class SrcPos(val line: Int, val col: Int) {
    fun read(c: Char): SrcPos = when (c) {
        '\n' -> SrcPos(line + 1, 0)
        else -> SrcPos(line, col + 1)
    }

    companion object {
        val Start = SrcPos(0, 0)
    }
}

/**
 * [HitLexeme] with source file position reference
 */
data class LexRef(val lex: HitLexeme, val pos: SrcPos)

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
    data class InvalidIdent(val pos: SrcPos) : LexError()

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
    data class Success(val tokens: List<LexRef>, val ident: Int, private val pos: SrcPos) : LexReader() {
        override fun read(c: Char): LexReader = ReadingIdent(0, this, pos, pos).read(c)

        fun addIdent(): Success = Success(
            tokens + LexRef(HitLexeme.Ident, pos),
            ident + IDENT_SIZE, pos
        )

        fun addDedent(): Success = Success(
            tokens + LexRef(HitLexeme.Dedent, pos),
            ident - IDENT_SIZE, pos
        )

        override fun finish(): LexReader = Success(
            tokens + (ident downTo IDENT_SIZE step IDENT_SIZE).map { LexRef(HitLexeme.Dedent, pos) },
            0,
            pos
        )
    }

    /**
     * Reading line ident
     */
    data class ReadingIdent(
        private val ident: Int,
        private val acc: Success,
        private val startPos: SrcPos,
        private val endPos: SrcPos
    ) :
        LexReader() {
        override fun read(c: Char): LexReader {
            return when (c) {
                ' ' -> ReadingIdent(ident + 1, acc, startPos, endPos.read(c))
                '\t' -> ReadingIdent(ident + IDENT_SIZE, acc, startPos, endPos.read(c))
                else -> WaitingSymbol(ident, acc, endPos).read(c)
            }
        }

        override fun finish(): LexReader = acc.finish()
    }

    /**
     * Waiting for [HitLexeme.Symbol] valid character state (after ident or previous symbol read)
     */
    data class WaitingSymbol(private val ident: Int, private val acc: Success, private val pos: SrcPos) :
        LexReader() {
        override fun read(c: Char): LexReader {
            return when {
                isLineBreak(c) -> acc.copy(pos = pos.read(c)) // end of line, done
                isSymbolChar(c) -> when {
                    ident == acc.ident -> ReadingSymbol(Character.toString(c), ident, acc, pos, pos.read(c))
                    ident == (acc.ident + IDENT_SIZE) -> WaitingSymbol(ident, acc.addIdent(), pos).read(c)
                    ident < acc.ident -> WaitingSymbol(ident, acc.addDedent(), pos).read(c)
                    else -> Failure(LexError.InvalidIdent(pos))
                }
                else -> this.copy(pos = pos.read(c)) // ignore char, waiting symbol
            }
        }

        override fun finish(): LexReader = acc.finish()
    }

    /**
     * Reading [HitLexeme.Symbol] in progress
     */
    data class ReadingSymbol(
        private val sym: String,
        private val ident: Int,
        private val acc: Success,
        private val startPos: SrcPos,
        private val lastPos: SrcPos
    ) : LexReader() {
        override fun read(c: Char): LexReader {
            return when {
            // continue reading symbol
                isSymbolChar(c) -> ReadingSymbol(sym + c, ident, acc, startPos, lastPos.read(c))
            // finished reading symbol, waiting for next
                else -> if (sym.lastOrNull() == '\\') {
                    return ReadingSymbol(sym.dropLast(1) + c, ident, acc, startPos, lastPos.read(c))
                } else {
                    WaitingSymbol(
                        ident,
                        acc.copy(tokens = acc.tokens + LexRef(HitLexeme.Symbol(sym), startPos)),
                        lastPos
                    ).read(c)
                }
            }
        }

        override fun finish(): LexReader =
            acc.copy(tokens = acc.tokens + LexRef(HitLexeme.Symbol(sym), startPos)).finish()
    }

    /**
     * Failure state, ignores all inputs
     */
    data class Failure(val err: LexError) : LexReader() {
        override fun read(c: Char): LexReader = this

        override fun finish(): LexReader = this
    }

    companion object {
        val Empty = Success(emptyList(), 0, SrcPos.Start)

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
 * Parses input string [str] into [LexRef] list
 */
fun lexFull(str: String): Result<List<LexRef>, LexError> {
    val acc = str.fold<LexReader>(LexReader.Empty, { acc, c -> acc.read(c) }).finish()

    return when (acc) {
        is LexReader.Success -> Result.Success(acc.tokens)
        is LexReader.Failure -> Result.Failure(acc.err)
        else -> Result.Failure(LexError.InvalidFinalState)
    }
}

/**
 * Parses input string [str] into [HitLexeme] list
 */
fun lex(str: String): Result<List<HitLexeme>, LexError> {
    return lexFull(str).map { l -> l.map { it.lex } }
}
