package hittassign.dsl

import com.github.kittinunf.result.Result

sealed class HitLexeme

object EndOfFile : HitLexeme()

sealed class LexError : Exception()

/**
 * Parses input string [str] into [HitLexeme] list
 */
fun lex(str: String): Result<List<HitLexeme>, LexError> {
    // TODO implement me
    return Result.Success(listOf(EndOfFile))
}
