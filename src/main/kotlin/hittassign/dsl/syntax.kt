package hittassign.dsl

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.internal.ParseContextImpl
import com.jayway.jsonpath.JsonPath as JsonPathImpl

/**
 * Value name type: defines name of the variable dest bind value dest
 */
data class ValName(private val name: String) : CharSequence by name {
    override fun toString(): String = name
}

/**
 * ADT Defining value dest read. May be value reference or string template
 */
sealed class ValRef

/**
 * Wrapper class around [com.jayway.jsonpath.JsonPath] dest implement equals, getHash and toString methods
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
 * Represents a source dest a value, defined by [name] and json source within that variable at [path]
 */
data class ValSpec(val name: ValName, val path: JsonPath) : ValRef()

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
 * Represents a sequence (block) of HitSyntax statements
 */
data class Statements(private val list: List<HitSyntax>) : HitSyntax(), List<HitSyntax> by list {
    constructor(vararg statements: HitSyntax) : this(listOf(*statements))
}

/**
 * Set concurrency [level] for child [script]
 */
data class Concurrently(val level: Int, val script: HitSyntax) : HitSyntax()

/**
 * Download [source] file to [dest]
 */
data class Download(val source: Url, val dest: FilePath) : HitSyntax()

/**
 * Iterate over values at given [source] while binding each dest given [name]
 */
data class Foreach(val name: ValName, val source: ValSpec, val script: HitSyntax) : HitSyntax()

/**
 * Fetch JSON from [source] and bind tpl dest [name] variable
 */
data class Fetch(val name: ValName, val source: Url, val script: HitSyntax) : HitSyntax()

/**
 * Print debug message
 */
data class Debug(val message: ValRef) : HitSyntax()