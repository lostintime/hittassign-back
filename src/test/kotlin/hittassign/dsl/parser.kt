package hittassign.dsl

import com.github.kittinunf.result.Result
import kotlin.test.assertEquals
import org.junit.Test
import kotlin.test.assertTrue


class TestDslParser {
    @Test
    fun `empty lex parses successfully`() {
        assertEquals(
            Result.Success(Statements()),
            parse(emptyList())
        )
    }

    @Test
    fun `parse debug`() {
        assertEquals(
            Result.Success(Statements(Debug(StringTpl(ConstStrPart("hello"))))),
            parse(listOf(HitLexeme.Symbol("debug"), HitLexeme.Symbol("hello")))
        )
    }

    @Test
    fun `parse fetch`() {
        assertEquals(
            Result.Success(
                Statements(
                    Fetch(
                        ValBind("n"),
                        StringTpl(ConstStrPart("http://test.com/api.json")),
                        Statements(Debug(StringTpl(ConstStrPart("hello"))))
                    )
                )
            ),
            parse(
                listOf(
                    HitLexeme.Symbol("fetch"),
                    HitLexeme.Symbol("n"),
                    HitLexeme.Symbol("http://test.com/api.json"),
                    HitLexeme.Ident,
                    HitLexeme.Symbol("debug"),
                    HitLexeme.Symbol("hello"),
                    HitLexeme.Dedent
                )
            )
        )
    }

    @Test
    fun `parse foreach`() {
        // FIXME: this fails because JsonPath doesn't implement equals() method
        assertEquals(
            Result.Success(
                Statements(
                    Foreach(
                        ValBind("n"),
                        ValSpec(ValBind("u"), JsonPath.compile("$")),
                        Statements(Debug(StringTpl(ConstStrPart("hello"))))
                    )
                )
            ),
            parse(
                listOf(
                    HitLexeme.Symbol("foreach"),
                    HitLexeme.Symbol("n"),
                    HitLexeme.Symbol("u"),
                    HitLexeme.Ident,
                    HitLexeme.Symbol("debug"),
                    HitLexeme.Symbol("hello"),
                    HitLexeme.Dedent
                )
            )
        )
    }

    @Test
    fun `parse download`() {
        assertEquals(
            Result.Success(
                Statements(
                    Download(
                        StringTpl(ConstStrPart("https://google.com")),
                        StringTpl(ConstStrPart("/tmp/google-com.txt"))
                    )
                )
            ),
            parse(
                listOf(
                    HitLexeme.Symbol("download"),
                    HitLexeme.Symbol("https://google.com"),
                    HitLexeme.Symbol("/tmp/google-com.txt")
                )
            )
        )
    }

    @Test
    fun `parse concurrently`() {
        assertEquals(
            Result.Success(
                Statements(
                    Concurrently(5, Statements(Debug(StringTpl(ConstStrPart("hello")))))
                )
            ),
            parse(
                listOf(
                    HitLexeme.Symbol("concurrently"),
                    HitLexeme.Symbol("5"),
                    HitLexeme.Ident,
                    HitLexeme.Symbol("debug"),
                    HitLexeme.Symbol("hello"),
                    HitLexeme.Dedent
                )
            )
        )
    }

    @Test
    fun `multiple statements parsed successfully`() {
        assertEquals(
            Result.Success(
                Statements(
                    Debug(StringTpl(ConstStrPart("hello"))),
                    Debug(StringTpl(ConstStrPart("there"))),
                    Debug(StringTpl(ConstStrPart("!")))
                )
            ),
            parse(
                listOf(
                    HitLexeme.Symbol("debug"), HitLexeme.Symbol("hello"),
                    HitLexeme.Symbol("debug"), HitLexeme.Symbol("there"),
                    HitLexeme.Symbol("debug"), HitLexeme.Symbol("!")
                )
            )
        )
    }

    @Test
    fun `fails to parse unknown statement`() {
        assertTrue(
            parse(listOf(HitLexeme.Symbol("aloha"))) is Result.Failure
        )
    }
}

class TestStringTpl {
    @Test
    fun `parse StringTpl from empty string`() {
        assertEquals(
            Result.Success(StringTpl()),
            stringTpl("")
        )
    }

    @Test
    fun `parse StringTpl from non-empty string`() {
        assertEquals(
            Result.Success(StringTpl(ConstStrPart("hello there"))),
            stringTpl("hello there")
        )
    }

    @Test
    fun `parse StringTpl from value reference`() {
        assertEquals(
            Result.Success(StringTpl(ValSpecPart(ValSpec(ValBind("user"), JsonPath.compile("$.name"))))),
            stringTpl("{user.name}")
        )
    }

    @Test
    fun `parse StringTpl with comma separated values`() {
        assertEquals(
            Result.Success(
                StringTpl(
                    ConstStrPart("ll="),
                    ValSpecPart(ValSpec(ValBind("l"), JsonPath.compile("$.lat"))),
                    ConstStrPart(","),
                    ValSpecPart(ValSpec(ValBind("l"), JsonPath.compile("$.lng")))
                )
            ),
            stringTpl("ll={l.lat,l.lng}")
        )
    }
}

class TestJsonPath {
    // TODO add JsonPath validation
}
