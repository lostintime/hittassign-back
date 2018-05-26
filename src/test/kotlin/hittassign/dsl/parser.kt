package hittassign.dsl

import com.github.kittinunf.result.Result
import kotlin.test.assertEquals
import org.junit.Test


class TestValKey {
//    @Test
//    fun `can be built from valid strings`() {
//        assertTrue(valKey("one") is Result.Success, "\"one\" is valid ValBind")
//        assertTrue(valKey("\$one") is Result.Success, "\"\$one\" is valid ValBind")
//        assertTrue(valKey("_one") is Result.Success, "\"_one\" is valid ValBind")
//        assertTrue(valKey("_o_n_e") is Result.Success, "\"_o_n_e\" is valid ValBind")
//        assertTrue(valKey("_O_N") is Result.Success, "\"_O_N\" is valid ValBind")
//        assertTrue(valKey("_1_2") is Result.Success, "\"_1_2\" is valid ValBind")
//        assertTrue(valKey("_$2") is Result.Success, "\"_\$2\" is valid ValBind")
//    }
//
//    @Test
//    fun `cannot be built from invalid strings`() {
//        assertTrue(valKey("") is Result.Failure, "\"\" is not a valid ValBind")
//        assertTrue(valKey("0") is Result.Failure, "\"0\" is not a valid ValBind")
//        assertTrue(valKey("100") is Result.Failure, "\"100\" is not a valid ValBind")
//        assertTrue(valKey(" ") is Result.Failure, "\" \" is not a valid ValBind")
//        assertTrue(valKey("_ a") is Result.Failure, "\"_ a\" is not a valid ValBind")
//        assertTrue(valKey("_\\a") is Result.Failure, "\"_\\a\" is not a valid ValBind")
//    }
//
//    @Test
//    fun `can be converted to string`() {
//        assertEquals(ValBind.of("hello_there").get().toString(), "hello_there")
//        assertNotEquals(ValBind.of("hello_there").get().toString(), "hello_here")
//    }
//
//    @Test
//    fun `is comparable`() {
//        assertEquals(ValBind.of("one").get(), ValBind.of("one").get())
//        assertNotEquals(ValBind.of("one").get(), ValBind.of("two").get())
//    }
}

class TestJsonPath {
    // TODO add JsonPath validation
}

class TestValPath {
    @Test
    fun `can be built from valid strings`() {
        assertEquals(valPath("a"), Result.Success(ValSpec(ValBind("a"), "\$")))
        assertEquals(valPath("a.b.c"), Result.Success(ValSpec(ValBind("a"), "\$.b.c")))
        assertEquals(valPath("v.items[0].name"), Result.Success(ValSpec(ValBind("v"), "\$.items[0].name")))
    }

    @Test
    fun `cannot be built from invalid strings()`() {
        assertEquals(valPath(""), Result.Failure(InvalidValPath), "ValSpec cannot be empty")
        assertEquals(valPath("a b c.items[0]"), Result.Failure(InvalidValPath), "ValBind cannot contain spaces")
        assertEquals(valPath("var/one.a.b.c.list[10].item"), Result.Failure(InvalidValPath), "ValBind cannot contain /")
    }
}

class TestStringTpl {
    @Test
    fun `is comparable`() {

    }

    @Test
    fun `toString returns original string`() {

    }

}