package hittassign.dsl

import com.github.kittinunf.result.Result
import org.junit.Test
import kotlin.test.assertEquals

class TestLex {
    @Test
    fun `it reads empty string`() {
        assertEquals(Result.Success(emptyList()), lex(""), "empty string parsed as empty Lexeme list")
    }

    @Test
    fun `newline parsed as Newline lexeme`() {
        assertEquals(
            Result.Success(listOf(HitLexeme.Newline)),
            lex("\n"),
            "newline parsed ot Newline lexeme"
        )

        assertEquals(
            Result.Success(listOf(HitLexeme.Newline, HitLexeme.Newline)),
            lex("\n\n"),
            "newline parsed ot Newline lexeme"
        )
    }

    @Test
    fun `it reads basic symbols`() {
        assertEquals(
            Result.Success(listOf(HitLexeme.Symbol("debug"), HitLexeme.Newline)),
            lex("debug"),
            "simple symbol parsed"
        )
    }

    @Test
    fun `it parses multiple lines`() {
        val script = "one\ntwo\nthree\n"

        assertEquals(
            Result.Success(
                listOf(
                    HitLexeme.Symbol("one"),
                    HitLexeme.Newline,
                    HitLexeme.Symbol("two"),
                    HitLexeme.Newline,
                    HitLexeme.Symbol("three"),
                    HitLexeme.Newline
                )
            ),
            lex(script)
        )
    }

    @Test
    fun `it parses last line`() {
        val script = "doit"

        assertEquals(
            Result.Success(listOf(HitLexeme.Symbol("doit"), HitLexeme.Newline)),
            lex(script)
        )
    }
}