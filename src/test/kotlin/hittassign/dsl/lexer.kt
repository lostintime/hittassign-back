package hittassign.dsl

import com.github.kittinunf.result.Result
import org.junit.Test
import kotlin.test.assertEquals

class TestLexReader {
    @Test
    fun `it reads empty string`() {
        assertEquals(Result.Success(emptyList()), lex(""), "empty string parsed as empty Lexeme list")
    }

    @Test
    fun `it ignores space-only lines`() {
        assertEquals(
            Result.Success(emptyList()),
            lex("   \n   \n   "),
            "empty lines or space-only lines ignored"
        )
    }

    @Test
    fun `ignores empty lines`() {
        assertEquals(
            Result.Success(
                listOf(
                    HitLexeme.Symbol("one"),
                    HitLexeme.Symbol("two"),
                    HitLexeme.Symbol("three")
                )
            ),
            lex("one\n\n   \ntwo\nthree\n")
        )
    }

    @Test
    fun `ignores trailing space`() {
        assertEquals(
            Result.Success(
                listOf(
                    HitLexeme.Symbol("one"),
                    HitLexeme.Symbol("two")
                )
            ),
            lex("one       \n\n   \ntwo\n\n")
        )
    }

    @Test
    fun `it reads basic symbols`() {
        assertEquals(
            Result.Success(listOf(HitLexeme.Symbol("debug"))),
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
                    HitLexeme.Symbol("two"),
                    HitLexeme.Symbol("three")
                )
            ),
            lex(script)
        )
    }

    @Test
    fun `it parses last line`() {
        val script = "doit"

        assertEquals(
            Result.Success(listOf(HitLexeme.Symbol("doit"))),
            lex(script)
        )
    }

    @Test
    fun `it parses idents`() {
        val script = "line1\n  line2\n"

        assertEquals(
            Result.Success(
                listOf(
                    HitLexeme.Symbol("line1"),
                    HitLexeme.Ident,
                    HitLexeme.Symbol("line2"),
                    HitLexeme.Dedent
                )
            ),
            lex(script)
        )
    }
}