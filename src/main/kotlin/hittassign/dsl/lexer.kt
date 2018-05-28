package hittassign.dsl

import com.github.kittinunf.result.Result

/**
 * Language lexemes ADT
 */
sealed class HitLexeme {
    object Newline : HitLexeme()

    data class Ident(val level: Int) : HitLexeme()

    data class Symbol(val sym: String) : HitLexeme() // symbol or string literal
}

object LexError : Exception()

/**
 * Ident reading state machine
 */
sealed class IdentReader {
    abstract fun read(c: Char): IdentReader

    object Empty : IdentReader() {
        override fun read(c: Char): IdentReader = when (c) {
            ' ' -> Reading(1)
            '\t' -> Reading(2)
            else -> Done(0)
        }
    }

    data class Reading(val len: Int) : IdentReader() {
        override fun read(c: Char): IdentReader = when (c) {
            ' ' -> Reading(len + 1)
            '\t' -> Reading(len + 2)
            else -> Done(len)
        }
    }

    data class Done(val len: Int) : IdentReader() {
        override fun read(c: Char): IdentReader = this
    }
}

/**
 * Symbol reading state machine: starts with Empty state, ends with Done or DoneEmpty states
 */
sealed class SymbolReader {
    abstract fun read(c: Char): SymbolReader

    object Empty : SymbolReader() {
        override fun read(c: Char): SymbolReader = if (isSymbolChar(c)) {
            Reading(Character.toString(c))
        } else {
            DoneEmpty // finished without getting any valid symbol chars
        }
    }

    data class Reading(val sym: String) : SymbolReader() {
        override fun read(c: Char): SymbolReader = if (isSymbolChar(c)) {
            Reading(sym + c)
        } else {
            Done(sym)
        }
    }

    data class Done(val sym: String) : SymbolReader() {
        override fun read(c: Char): SymbolReader = this
    }

    object DoneEmpty : SymbolReader() {
        override fun read(c: Char): SymbolReader = this
    }

    companion object {
        /**
         * TODO improve valid symbol characters validation
         */
        fun isSymbolChar(c: Char): Boolean = when (c) {
            ' ' -> false
            '\t' -> false
            '\r' -> false
            '\n' -> false
            else -> true
        }
    }
}

sealed class LineReader {
    abstract fun read(c: Char): LineReader

    object Empty : LineReader() {
        override fun read(c: Char): LineReader = OnIdent(IdentReader.Empty).read(c)
    }

    data class OnIdent(val ident: IdentReader) : LineReader() {
        override fun read(c: Char): LineReader {
            return if (isLineBreak(c)) {
                Done(emptyList()) // ignoring ident
            } else {
                val ident = IdentReader.Empty.read(c)
                when (ident) {
                    is IdentReader.Empty -> OnIdent(ident) // continue reading
                    is IdentReader.Reading -> OnIdent(ident) // continue reading
                    is IdentReader.Done ->
                        // ident is done on line break or symbol start
                        OnSymbol(listOf(HitLexeme.Ident(ident.len)), SymbolReader.Empty).read(c)
                }
            }
        }
    }

    data class OnSymbol(val prev: List<HitLexeme>, val acc: SymbolReader) : LineReader() {
        override fun read(c: Char): LineReader {
            val sym = acc.read(c)

            return when (sym) {
                is SymbolReader.Empty -> if (isLineBreak(c)) Done(prev) else OnSymbol(prev, acc)
            // this shouldn't happen, symbol reading should finish on line break
                is SymbolReader.Reading -> if (isLineBreak(c)) Invalid else OnSymbol(prev, acc)
                is SymbolReader.DoneEmpty -> if (isLineBreak(c)) Done(prev) else OnSymbol(prev, SymbolReader.Empty)
                is SymbolReader.Done -> if (isLineBreak(c)) {
                    Done(prev + HitLexeme.Symbol(sym.sym))
                } else {
                    OnSymbol(prev + HitLexeme.Symbol(sym.sym), SymbolReader.Empty)
                }
            }
        }
    }

    data class Done(val tokens: List<HitLexeme>) : LineReader() {
        override fun read(c: Char): LineReader = this
    }

    object Invalid : LineReader() {
        override fun read(c: Char): LineReader = this
    }

    companion object {
        fun isLineBreak(c: Char) = c == '\r' || c == '\n'
    }
}

sealed class LexReader {
    abstract fun read(c: Char): LexReader

    data class Success(val tokens: List<HitLexeme>) : LexReader() {
        override fun read(c: Char): LexReader = OnLine(tokens, LineReader.Empty).read(c)
    }

    data class OnLine(val tokens: List<HitLexeme>, val line: LineReader) : LexReader() {
        override fun read(c: Char): LexReader {
            val l = line.read(c)
            return when (l) {
                is LineReader.Empty -> OnLine(tokens, line)
                is LineReader.OnIdent -> OnLine(tokens, line)
                is LineReader.OnSymbol -> OnLine(tokens, line)
                is LineReader.Invalid -> Invalid
                is LineReader.Done -> {
                    println("Line parsed: $tokens")
                    Success(tokens + l.tokens + HitLexeme.Newline)
                }
            }
        }
    }

    object Invalid : LexReader() {
        override fun read(c: Char): LexReader = this
    }

    companion object {
        val Empty = Success(emptyList())
    }
}

/**
 * Parses input string [str] into [HitLexeme] list
 */
fun lex(str: String): Result<List<HitLexeme>, LexError> {
    val acc = str.fold<LexReader>(LexReader.Empty, { acc, c -> acc.read(c) })

    return when (acc) {
        is LexReader.Success -> Result.Success(acc.tokens)
        is LexReader.OnLine -> Result.Failure(LexError) // FIXME add detailed error message
        is LexReader.Invalid -> Result.Failure(LexError) // FIXME add detailed error message
    }
}
