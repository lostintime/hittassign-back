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
        // FIXME: line reading not completed for last line (no line break)
        assertEquals(
            Result.Success(listOf(HitLexeme.Symbol("debug"))),
            lex("debug"),
            "simple symbol parsed"
        )
    }

    @Test
    fun `it parses multiple lines`() {
        val script = "one\ntwo\nthree\n"

        println(lex(script))
    }
}