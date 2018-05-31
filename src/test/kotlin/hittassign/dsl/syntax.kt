package hittassign.dsl

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Test


class TestValBind {
    @Test
    fun `can be compared`() {
        assertEquals(ValName("one"), ValName("one"), "equals method implemented")

        assertNotEquals(ValName("one"), ValName("two"), "equals method implemented")

        assertEquals(
            mapOf(ValName("one") to "one"),
            mapOf(ValName("one") to "one"),
            "hashCode method implemented"
        )

        assertNotEquals(
            mapOf(ValName("one") to "one"),
            mapOf(ValName("one") to "two"),
            "hashCode method implemented"
        )
    }

    @Test
    fun `toString implemented`() {
        assertEquals(ValName("a").toString(), "a")
        assertNotEquals(ValName("a").toString(), "b")
    }
}