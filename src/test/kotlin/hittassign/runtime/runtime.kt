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
                ValName("k") to JsonPath.parse("\"hello\"").json()
            )
        )

        assertEquals(
            Result.Success("hello"),
            ctx.getString(ValSpec(ValName("k"), JsonPath.compile("$"))),
            "json value extracted"
        )
    }

    @Test
    fun `get String from sub-path`() {
        val ctx = RuntimeContext(
            mapOf(
                ValName("z") to JsonPath.parse(
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
            ctx.getString(ValSpec(ValName("z"), JsonPath.compile("$.name"))),
            "got name from $.name"
        )

        assertEquals(
            Result.Success("London"),
            ctx.getString(ValSpec(ValName("z"), JsonPath.compile("$.city.name"))),
            "got name from $.city.name"
        )

        assertEquals(
            Result.Success("123"),
            ctx.getString(ValSpec(ValName("z"), JsonPath.compile("$.city.id"))),
            "got id from $.city.id"
        )

        assertEquals(
            Result.Success("45.6"),
            ctx.getString(ValSpec(ValName("z"), JsonPath.compile("$.city.size"))),
            "got size from $.city.size"
        )

        // JsonPath doesn't implement equals, using same instance
        val p1 = JsonPath.compile("$.city")
        assertEquals(
            Result.Failure(RuntimeError.InvalidValueType(ValName("z"), p1)),
            ctx.getString(ValSpec(ValName("z"), p1)),
            "unable get String at object source"
        )

        // JsonPath doesn't implement equals, using same instance
        val p2 = JsonPath.compile("$.items")
        assertEquals(
            Result.Failure(RuntimeError.InvalidValueType(ValName("z"), p2)),
            ctx.getString(ValSpec(ValName("z"), p2)),
            "unable get String at array source"
        )

        // JsonPath doesn't implement equals, using same instance
        val p3 = JsonPath.compile("$.enabled")
        assertEquals(
            Result.Failure(RuntimeError.InvalidValueType(ValName("z"), p3)),
            ctx.getString(ValSpec(ValName("z"), p3)),
            "unable get String at boolean source"
        )
    }

    @Test
    fun `get string from previous context`() {
        val ctx = RuntimeContext(
            mapOf(ValName("z") to JsonPath.parse("\"z-value\"").json()),
            RuntimeContext(
                mapOf(ValName("h") to JsonPath.parse("\"h-value\"").json())
            )
        )

        assertEquals(
            Result.Success("h-value"),
            ctx.getString(ValSpec(ValName("h"), JsonPath.compile("$"))),
            "json value extracted from parent context"
        )
    }

    @Test
    fun `get Iterable from root`() {
        val ctx = RuntimeContext(
            mapOf(
                ValName("k") to JsonPath.parse("[\"hello\", \"there\"]").json()
            )
        )

        val items = ctx.getIterable<String>(ValSpec(ValName("k"), JsonPath.compile("$")))
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
                ValName("a") to JsonPath.parse("""{"name": "John"}""").json()
            )
        )

        assertEquals(
            Result.Success("Hello John"),
            ctx.renderStringTpl(
                StringTpl(
                    ConstStrPart("Hello "),
                    ValSpecPart(ValSpec(ValName("a"), JsonPath.compile("$.name")))
                )
            ),
            "StringTpl  rendered successfully"
        )

        // JsonPath doesn't implement equals, using same instance
        val p1 = JsonPath.compile("$.surname")

        assertEquals(
            Result.Failure<String, RuntimeError>(RuntimeError.InvalidValueType(ValName("a"), p1)),
            ctx.renderStringTpl(
                StringTpl(
                    ConstStrPart("Hola "),
                    ValSpecPart(ValSpec(ValName("a"), p1))
                )
            )
        )

        assertEquals(
            Result.Failure<String, RuntimeError>(RuntimeError.ValueNotFound(ValName("b"))),
            ctx.renderStringTpl(
                StringTpl(
                    ConstStrPart("Hola "),
                    ValSpecPart(ValSpec(ValName("b"), JsonPath.compile("$.name")))
                )
            )
        )
    }
}