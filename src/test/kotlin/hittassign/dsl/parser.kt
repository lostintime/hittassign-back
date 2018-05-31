package hittassign.dsl

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
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
                        ValName("n"),
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
                        ValName("n"),
                        ValSpec(ValName("u"), JsonPath.compile("$")),
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

    @Test
    fun `more complex example`() {
        val source = """|
            |fetch h https://api.hitta.se/search/v7/app/combined/within/57.840703831916%3A11.728156448084002%2C57.66073920808401%3A11.908121071915998/?range.dest=101&range.from=1&geo.hint=57.75072152%3A11.81813876&sort.order=relevance&query=lunch
            |  foreach c h.result.companies.company
            |    fetch v https://api.foursquare.com/v2/venues/search?ll={c.address[0].coordinate.north,c.address[0].coordinate.east}&client_id=CLIENT_ID&client_secret=CLIENT_SECRET&intent=match&name={c.displayName}&v=20180401
            |      fetch d https://api.foursquare.com/v2/venues/\$\{v.response.venues[0].id\}/photos?client_id=CLIENT_ID&client_secret=CLIENT_SECRET&v=20180401
            |        foreach i d.response.photo.items
            |          download {i.prefix}original{i.suffix} /some_local_path/photos/{c.id}_{i.suffix}
            |""".trimMargin()

        assertTrue(lex(source).flatMap { parse(it) } is Result.Success)
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
            Result.Success(StringTpl(ValSpecPart(ValSpec(ValName("user"), JsonPath.compile("$.name"))))),
            stringTpl("{user.name}")
        )
    }

    @Test
    fun `parse StringTpl with comma separated values`() {
        assertEquals(
            Result.Success(
                StringTpl(
                    ConstStrPart("ll="),
                    ValSpecPart(ValSpec(ValName("l"), JsonPath.compile("$.lat"))),
                    ConstStrPart(","),
                    ValSpecPart(ValSpec(ValName("l"), JsonPath.compile("$.lng")))
                )
            ),
            stringTpl("ll={l.lat,l.lng}")
        )
    }
}
