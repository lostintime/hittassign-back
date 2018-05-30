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
 * Invalid idents will be shifted left (ex: 3 -> 2)
 */
sealed class IdentReader {
    abstract fun read(c: Char): IdentReader

    object Empty : IdentReader() {
        override fun read(c: Char): IdentReader = when (c) {
            ' ' -> Reading(1)
            '\t' -> Reading(2) // tabs weight = 2
            else -> DoneEmpty
        }
    }

    data class Reading(val len: Int) : IdentReader() {
        override fun read(c: Char): IdentReader = when (c) {
            ' ' -> Reading(len + 1)
            '\t' -> Reading(len + 2) // tabs weight = 2
            else -> Done(if ((len % 2) == 1) len - 1 else len) // auo-fixing ident by shifting left
        }
    }

    data class Done(val len: Int) : IdentReader() {
        override fun read(c: Char): IdentReader = this
    }

    object DoneEmpty : IdentReader() {
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

    data class Reading(val symbol: String) : SymbolReader() {
        override fun read(c: Char): SymbolReader = if (isSymbolChar(c)) {
            Reading(symbol + c)
        } else {
            Done(symbol)
        }
    }

    data class Done(val symbol: String) : SymbolReader() {
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

    abstract fun finish(): LineReader

    object Empty : LineReader() {
        override fun read(c: Char): LineReader = OnIdent(IdentReader.Empty).read(c)

        override fun finish(): LineReader = Done(emptyList())
    }

    data class OnIdent(val ident: IdentReader) : LineReader() {
        override fun read(c: Char): LineReader {
            return if (isLineBreak(c)) {
                Done(emptyList()) // ignoring ident
            } else {
                val ident = ident.read(c)
                when (ident) {
                    is IdentReader.Empty -> OnIdent(ident) // continue reading
                    is IdentReader.Reading -> OnIdent(ident) // continue reading
                // ident is done on line break or symbol start
                    is IdentReader.Done -> OnSymbol(listOf(HitLexeme.Ident(ident.len)), SymbolReader.Empty).read(c)
                    is IdentReader.DoneEmpty -> OnSymbol(emptyList(), SymbolReader.Empty).read(c)
                }
            }
        }

        override fun finish(): LineReader = Done(emptyList())
    }

    data class OnSymbol(val tokens: List<HitLexeme>, val sym: SymbolReader) : LineReader() {
        override fun read(c: Char): LineReader {
            val sym = sym.read(c)

            return when (sym) {
                is SymbolReader.Empty -> if (isLineBreak(c)) Done(tokens) else OnSymbol(tokens, sym)
            // this shouldn't happen, symbol reading should finish on line break
                is SymbolReader.Reading -> if (isLineBreak(c)) Invalid else OnSymbol(tokens, sym)
                is SymbolReader.DoneEmpty -> if (isLineBreak(c)) Done(tokens) else OnSymbol(tokens, SymbolReader.Empty)
                is SymbolReader.Done -> if (isLineBreak(c)) {
                    Done(tokens + HitLexeme.Symbol(sym.symbol))
                } else {
                    OnSymbol(tokens + HitLexeme.Symbol(sym.symbol), SymbolReader.Empty)
                }
            }
        }

        override fun finish(): LineReader {
            return when (sym) {
                is SymbolReader.Empty -> Done(tokens)
                is SymbolReader.Reading -> Done(tokens + HitLexeme.Symbol(sym.symbol))
                is SymbolReader.Done -> Done(tokens + HitLexeme.Symbol(sym.symbol))
                is SymbolReader.DoneEmpty -> Done(tokens + HitLexeme.Newline)
            }
        }
    }

    data class Done(val tokens: List<HitLexeme>) : LineReader() {
        override fun read(c: Char): LineReader = this

        override fun finish(): LineReader = this
    }

    object Invalid : LineReader() {
        override fun read(c: Char): LineReader = this

        override fun finish(): LineReader = this
    }

    companion object {
        fun isLineBreak(c: Char) = c == '\r' || c == '\n'
    }
}

sealed class LexReader {
    abstract fun read(c: Char): LexReader

    abstract fun finish(): LexReader

    data class Success(val tokens: List<HitLexeme>) : LexReader() {
        override fun read(c: Char): LexReader = OnLine(tokens, LineReader.Empty).read(c)

        override fun finish(): LexReader = this
    }

    data class OnLine(val tokens: List<HitLexeme>, val line: LineReader) : LexReader() {
        override fun read(c: Char): LexReader {
            val line = line.read(c)

            return when (line) {
                is LineReader.Empty -> OnLine(tokens, line)
                is LineReader.OnIdent -> OnLine(tokens, line)
                is LineReader.OnSymbol -> OnLine(tokens, line)
                is LineReader.Invalid -> Invalid
                is LineReader.Done -> Success(tokens + line.tokens + HitLexeme.Newline)
            }
        }

        override fun finish(): LexReader {
            val line = line.finish()

            return when (line) {
                is LineReader.Empty -> Success(tokens)
                is LineReader.OnIdent -> Success(tokens)
                is LineReader.OnSymbol -> Invalid
                is LineReader.Invalid -> Invalid
                is LineReader.Done -> Success(tokens + line.tokens + HitLexeme.Newline)
            }
        }
    }

    object Invalid : LexReader() {
        override fun read(c: Char): LexReader = this

        override fun finish(): LexReader = this
    }

    companion object {
        val Empty = Success(emptyList())
    }
}

/**
 * Parses input string [str] into [HitLexeme] list
 */
fun lex(str: String): Result<List<HitLexeme>, LexError> {
    val acc = str.fold<LexReader>(LexReader.Empty, { acc, c -> acc.read(c) }).finish()

    return when (acc) {
        is LexReader.Success -> Result.Success(acc.tokens)
        is LexReader.OnLine -> Result.Failure(LexError) // FIXME add detailed error message
        is LexReader.Invalid -> Result.Failure(LexError) // FIXME add detailed error message
    }
}
