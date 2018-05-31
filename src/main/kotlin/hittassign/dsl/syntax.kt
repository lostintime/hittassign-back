package hittassign.dsl

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.internal.ParseContextImpl
import com.jayway.jsonpath.JsonPath as JsonPathImpl

/**
 * Value bind name type - Defines name of the variable to bind value to
 */
data class ValBind(val name: String) : CharSequence by name {
    override fun toString(): String = name
}

/**
 * ADT Defining value to read. May be value reference or string template
 */
sealed class ValRef

/**
 * Wrapper class around [com.jayway.jsonpath.JsonPath] to implement equals, getHash and toString methods
 */
class JsonPath(val compiled: JsonPathImpl) {
    override fun equals(other: Any?): Boolean {
        return when {
            other === this -> true
            other is String -> other == compiled.path
            other is JsonPath -> other.compiled.path == compiled.path
            else -> false
        }
    }

    override fun hashCode(): Int {
        return compiled.hashCode()
    }

    override fun toString(): String {
        return compiled.path
    }

    companion object {
        fun compile(jsonPath: String): JsonPath = JsonPath(JsonPathImpl.compile(jsonPath))

        fun parse(json: Any): DocumentContext {
            return ParseContextImpl().parse(json)
        }

        fun parse(json: String): DocumentContext {
            return ParseContextImpl().parse(json)
        }
    }
}

/**
 * Represents a source to a value, defined by key (variable name) and json source within that variable
 */
data class ValSpec(val key: ValBind, val path: JsonPath) : ValRef()

/**
 * ADT defining StringTpl parts types.
 * Maybe constant string or value reference
 */
sealed class StringTplPart

/**
 * Constant string part type
 */
data class ConstStrPart(val str: String) : StringTplPart()

/**
 * Value spec part type
 */
data class ValSpecPart(val ref: ValSpec) : StringTplPart()

/**
 * String template data type, ex: https://foursquare/match/{c.long,c.lat}
 */
data class StringTpl(val parts: List<StringTplPart>) : ValRef() {
    constructor(vararg parts: StringTplPart) : this(listOf(*parts))
}

/**
 * Url type alias (for future refinements)
 */
typealias Url = ValRef

/**
 * File source type alias (for future refinements)
 */
typealias FilePath = ValRef

/**
 * Language Syntax ADT
 */
sealed class HitSyntax

/**
 * Represents a sequence of HitSyntax concurrently
 */
data class Statements(private val list: List<HitSyntax>) : HitSyntax(), List<HitSyntax> by list {
    constructor(vararg statements: HitSyntax) : this(listOf(*statements))
}

/**
 * Set concurrency level
 */
data class Concurrently(val level: Int, val script: HitSyntax) : HitSyntax()

/**
 * Download source may be string template or data bind spec
 */
data class Download(val source: Url, val to: FilePath) : HitSyntax()

/**
 * Iterate over values at given [source] while binding each to given [key]
 */
data class Foreach(val key: ValBind, val source: ValSpec, val script: HitSyntax) : HitSyntax()

/**
 * Fetch JSON from [source] and bind tpl to [key] variable
 */
data class Fetch(val key: ValBind, val source: Url, val script: HitSyntax) : HitSyntax()

/**
 * Print debug message
 */
data class Debug(val message: StringTpl) : HitSyntax()