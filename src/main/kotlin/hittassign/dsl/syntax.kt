package hittassign.dsl

import com.jayway.jsonpath.JsonPath

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
 * Represents a path to a value, defined by key (variable name) and json path within that variable
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
 * File path type alias (for future refinements)
 */
typealias FilePath = ValRef

/**
 * Language Syntax ADT
 */
sealed class HitSyntax

/**
 * Represents a sequence of HitSyntax statements
 */
data class Statements(private val list: List<HitSyntax>) : HitSyntax(), List<HitSyntax> by list {
    constructor(vararg statements: HitSyntax) : this(listOf(*statements))
}

/**
 * Download source may be string template or data bind spec
 */
data class Download(val source: Url, val to: FilePath) : HitSyntax()

/**
 * Iterate over values at given [path] while binding each to given [key]
 */
data class Foreach(val key: ValBind, val path: ValSpec, val statements: Statements) : HitSyntax()

/**
 * Fetch JSON from [source] and bind tpl to [key] variable
 */
data class Fetch(val key: ValBind, val source: Url, val statements: Statements) : HitSyntax()

/**
 * Print debug message
 */
data class Debug(val message: StringTpl) : HitSyntax()