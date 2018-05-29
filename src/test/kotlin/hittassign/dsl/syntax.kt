package hittassign.dsl

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Test


class TestValBind {
    @Test
    fun `can be compared`() {
        assertEquals(ValBind("one"), ValBind("one"), "equals method implemented")

        assertNotEquals(ValBind("one"), ValBind("two"), "equals method implemented")

        assertEquals(
            mapOf(ValBind("one") to "one"),
            mapOf(ValBind("one") to "one"),
            "hashCode method implemented"
        )

        assertNotEquals(
            mapOf(ValBind("one") to "one"),
            mapOf(ValBind("one") to "two"),
            "hashCode method implemented"
        )
    }

    @Test
    fun `toString implemented`() {
        assertEquals(ValBind("a").toString(), "a")
        assertNotEquals(ValBind("a").toString(), "b")
    }
}