package hittassign.dsl

import com.github.kittinunf.result.Result

/**
 * Specification for a tpl. Maybe constant or tpl (variable)
 */
sealed class ValSpec

/**
 * Invalid bind key error
 */
object InvalidValKey : Exception()

/**
 * Value bind key name type (https://docs.oracle.com/cd/E19798-01/821-1841/bnbuk/index.html)
 */
class ValKey private constructor(private val str: String) : ValSpec(), CharSequence by str {

    override fun toString(): String = str

    companion object {
        /**
         * Valid [ValKey] string Regex
         */
        private val RE = Regex("""^[a-zA-Z_${'$'}][a-zA-Z0-9_${'$'}]*$""")

        /**
         * Returns true if [key] is a valid [ValKey] string
         */
        private fun isValid(key: String): Boolean = RE.matches(key)

        /**
         * Validate and create new [ValKey] instance from [key] string
         */
        fun of(key: String): Result<ValKey, InvalidValKey> {
            return if (isValid(key)) {
                Result.Success(ValKey(key))
            } else {
                Result.Failure(InvalidValKey)
            }
        }
    }
}

/**
 * Shorter name for [ValKey.of] builder
 */
fun valKey(str: String) = ValKey.of(str)

typealias JsonPath = String

data class ValPath(val key: ValKey, val path: JsonPath) : ValSpec()

// fun valPath(spec: String): Result<ValPath, InvalidValPath> = ...

/**
 * String template data type, ex: https://foursquare/match/{c.long,c.lat}
 */
data class StringTpl(val tpl: String) : ValSpec(), CharSequence by tpl

// fun stringTpl(tpl: String): Result<StringTpl, InvalidStringTpl> = ...

/**
 * Url type alias (for future refinements)
 */
typealias Url = ValSpec

/**
 * File path type alias (for future refinements)
 */
typealias FilePath = ValSpec

/**
 * Language syntax ADT
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
data class Foreach(val key: ValKey, val path: ValPath, val statements: Statements) : HitSyntax()

/**
 * Fetch JSON from [source] and bind tpl to [key] variable
 */
data class Fetch(val key: ValKey, val source: Url, val statements: Statements) : HitSyntax()
