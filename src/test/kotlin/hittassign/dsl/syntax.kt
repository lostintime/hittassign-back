package hittassign.dsl

import com.github.kittinunf.result.Result
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import kotlin.test.assertNotEquals

class TestBindKey {
    @Test
    fun `can be built from valid strings`() {
        assertTrue(valKey("one") is Result.Success, "\"one\" is valid ValKey")
        assertTrue(valKey("\$one") is Result.Success, "\"\$one\" is valid ValKey")
        assertTrue(valKey("_one") is Result.Success, "\"_one\" is valid ValKey")
        assertTrue(valKey("_o_n_e") is Result.Success, "\"_o_n_e\" is valid ValKey")
        assertTrue(valKey("_O_N") is Result.Success, "\"_O_N\" is valid ValKey")
        assertTrue(valKey("_1_2") is Result.Success, "\"_1_2\" is valid ValKey")
        assertTrue(valKey("_$2") is Result.Success, "\"_\$2\" is valid ValKey")
    }

    @Test
    fun `cannot be built from from invalid strings`() {
        assertTrue(valKey("") is Result.Failure, "\"\" is not a valid ValKey")
        assertTrue(valKey("0") is Result.Failure, "\"0\" is not a valid ValKey")
        assertTrue(valKey("100") is Result.Failure, "\"100\" is not a valid ValKey")
        assertTrue(valKey(" ") is Result.Failure, "\" \" is not a valid ValKey")
        assertTrue(valKey("_ a") is Result.Failure, "\"_ a\" is not a valid ValKey")
        assertTrue(valKey("_\\a") is Result.Failure, "\"_\\a\" is not a valid ValKey")
    }

    @Test
    fun `can be converted to string`() {
        assertEquals(ValKey.of("hello_there").get().toString(), "hello_there")
        assertNotEquals(ValKey.of("hello_there").get().toString(), "hello_here")
    }
}