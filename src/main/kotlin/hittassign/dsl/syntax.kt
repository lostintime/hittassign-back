package hittassign.dsl

import com.github.kittinunf.result.Result

/**
 * String template data type, ex: https://foursquare/match/{c.long,c.lat}
 */
typealias StringTpl = String

/**
 * Specification for a value. Maybe constant or value (variable)
 */
sealed class ValSpec

/**
 * Invalid bind key error
 */
object InvalidBindKey : Exception()

/**
 * Value bind key name type (https://docs.oracle.com/cd/E19798-01/821-1841/bnbuk/index.html)
 */
class BindKey private constructor(private val str: String) : CharSequence by str {

    override fun toString(): String = str

    companion object {
        /**
         * Valid [BindKey] string Regex
         */
        private val RE = Regex("""^[a-zA-Z_${'$'}][a-zA-Z0-9_${'$'}]*$""")

        /**
         * Returns true if [key] is a valid [BindKey] string
         */
        private fun isValid(key: String): Boolean = RE.matches(key)

        /**
         * Validate and create new [BindKey] instance from [key] string
         */
        fun of(key: String): Result<BindKey, InvalidBindKey> {
            return if (isValid(key)) {
                Result.Success(BindKey(key))
            } else {
                Result.Failure(InvalidBindKey)
            }
        }
    }
}

/**
 * Shorter name for [BindKey.of] builder
 */
fun bindKey(str: String) = BindKey.of(str)

typealias JsonPath = String

data class BindSpec(val key: BindKey, val path: JsonPath) : ValSpec()

data class Const(val value: StringTpl) : ValSpec()

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
data class Foreach(
        val key: BindKey,
        val path: BindSpec,
        val statements: Statements
) : HitSyntax()

/**
 * Fetch JSON from [source] and bind value to [key] variable
 */
data class Fetch(
        val key: BindKey,
        val source: Url,
        val statements: Statements
) : HitSyntax()
