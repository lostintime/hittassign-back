package hittassign.runtime

import com.github.kittinunf.result.Result
import hittassign.dsl.*
import kotlin.test.assertEquals
import org.junit.Test
import kotlin.test.assertTrue

class TestRuntimeContext {
    @Test
    fun `get String from root`() {
        val ctx = RuntimeContext(
            mapOf(
                ValBind("k") to JsonPath.parse("\"hello\"").json()
            )
        )

        assertEquals(
            Result.Success("hello"),
            ctx.getString(ValSpec(ValBind("k"), JsonPath.compile("$"))),
            "json value extracted"
        )
    }

    @Test
    fun `get String from sub-path`() {
        val ctx = RuntimeContext(
            mapOf(
                ValBind("z") to JsonPath.parse(
                    """{
                       "name": "John",
                       "city": {
                            "name": "London",
                            "id": 123,
                            "size": 45.6
                       },
                       "items": [ "a", "b", "c" ],
                       "enabled": true
                    }"""
                ).json()
            )
        )

        assertEquals(
            Result.Success("John"),
            ctx.getString(ValSpec(ValBind("z"), JsonPath.compile("$.name"))),
            "got name from $.name"
        )

        assertEquals(
            Result.Success("London"),
            ctx.getString(ValSpec(ValBind("z"), JsonPath.compile("$.city.name"))),
            "got name from $.city.name"
        )

        assertEquals(
            Result.Success("123"),
            ctx.getString(ValSpec(ValBind("z"), JsonPath.compile("$.city.id"))),
            "got id from $.city.id"
        )

        assertEquals(
            Result.Success("45.6"),
            ctx.getString(ValSpec(ValBind("z"), JsonPath.compile("$.city.size"))),
            "got size from $.city.size"
        )

        // JsonPath doesn't implement equals, using same instance
        val p1 = JsonPath.compile("$.city")
        assertEquals(
            Result.Failure(InvalidValBindType(ValBind("z"), p1)),
            ctx.getString(ValSpec(ValBind("z"), p1)),
            "unable get String at object source"
        )

        // JsonPath doesn't implement equals, using same instance
        val p2 = JsonPath.compile("$.items")
        assertEquals(
            Result.Failure(InvalidValBindType(ValBind("z"), p2)),
            ctx.getString(ValSpec(ValBind("z"), p2)),
            "unable get String at array source"
        )

        // JsonPath doesn't implement equals, using same instance
        val p3 = JsonPath.compile("$.enabled")
        assertEquals(
            Result.Failure(InvalidValBindType(ValBind("z"), p3)),
            ctx.getString(ValSpec(ValBind("z"), p3)),
            "unable get String at boolean source"
        )
    }

    @Test
    fun `get string from previous context`() {
        val ctx = RuntimeContext(
            mapOf(ValBind("z") to JsonPath.parse("\"z-value\"").json()),
            RuntimeContext(
                mapOf(ValBind("h") to JsonPath.parse("\"h-value\"").json())
            )
        )

        assertEquals(
            Result.Success("h-value"),
            ctx.getString(ValSpec(ValBind("h"), JsonPath.compile("$"))),
            "json value extracted from parent context"
        )
    }

    @Test
    fun `get Iterable from root`() {
        val ctx = RuntimeContext(
            mapOf(
                ValBind("k") to JsonPath.parse("[\"hello\", \"there\"]").json()
            )
        )

        val items = ctx.getIterable<String>(ValSpec(ValBind("k"), JsonPath.compile("$")))
        assertTrue(items is Result.Success, "successfully loaded iterable for json array")
        val l = items.get().toList()
        assertTrue(l.size == 2)
        assertEquals(l[0], "hello")
        assertEquals(l[1], "there")
    }

    @Test
    fun `rendering string template`() {
        val ctx = RuntimeContext(
            mapOf(
                ValBind("a") to JsonPath.parse("""{"name": "John"}""").json()
            )
        )

        assertEquals(
            Result.Success("Hello John"),
            ctx.renderStringTpl(
                StringTpl(
                    ConstStrPart("Hello "),
                    ValSpecPart(ValSpec(ValBind("a"), JsonPath.compile("$.name")))
                )
            ),
            "StringTpl  rendered successfully"
        )

        // JsonPath doesn't implement equals, using same instance
        val p1 = JsonPath.compile("$.surname")

        assertEquals(
            Result.Failure<String, GetValError>(InvalidValBindType(ValBind("a"), p1)),
            ctx.renderStringTpl(
                StringTpl(
                    ConstStrPart("Hola "),
                    ValSpecPart(ValSpec(ValBind("a"), p1))
                )
            )
        )

        assertEquals(
            Result.Failure<String, GetValError>(ValBindNotFound(ValBind("b"))),
            ctx.renderStringTpl(
                StringTpl(
                    ConstStrPart("Hola "),
                    ValSpecPart(ValSpec(ValBind("b"), JsonPath.compile("$.name")))
                )
            )
        )
    }
}