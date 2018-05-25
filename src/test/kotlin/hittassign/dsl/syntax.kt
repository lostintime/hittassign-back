package hittassign.dsl

import com.github.kittinunf.result.Result
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import kotlin.test.assertNotEquals

class TestBindKey {
    @Test fun `can be built from valid strings`() {
        assertTrue(bindKey("one") is Result.Success, "\"one\" is valid BindKey")
        assertTrue(bindKey("\$one") is Result.Success, "\"\$one\" is valid BindKey")
        assertTrue(bindKey("_one") is Result.Success, "\"_one\" is valid BindKey")
        assertTrue(bindKey("_o_n_e") is Result.Success, "\"_o_n_e\" is valid BindKey")
        assertTrue(bindKey("_O_N") is Result.Success, "\"_O_N\" is valid BindKey")
        assertTrue(bindKey("_1_2") is Result.Success, "\"_1_2\" is valid BindKey")
        assertTrue(bindKey("_$2") is Result.Success, "\"_\$2\" is valid BindKey")
    }

    @Test fun `cannot be built from from invalid strings`() {
        assertTrue(bindKey("") is Result.Failure, "\"\" is not a valid BindKey")
        assertTrue(bindKey("0") is Result.Failure, "\"0\" is not a valid BindKey")
        assertTrue(bindKey("100") is Result.Failure, "\"100\" is not a valid BindKey")
        assertTrue(bindKey(" ") is Result.Failure, "\" \" is not a valid BindKey")
        assertTrue(bindKey("_ a") is Result.Failure, "\"_ a\" is not a valid BindKey")
        assertTrue(bindKey("_\\a") is Result.Failure, "\"_\\a\" is not a valid BindKey")
    }

    @Test fun `can be converted to string`() {
        assertEquals(BindKey.of("hello_there").get().toString(), "hello_there")
        assertNotEquals(BindKey.of("hello_there").get().toString(), "hello_here")
    }
}