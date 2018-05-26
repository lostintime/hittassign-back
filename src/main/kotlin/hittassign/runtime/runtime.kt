package hittassign.runtime

import com.github.kittinunf.result.*
import hittassign.dsl.*

/**
 * ADT defining errors happening on reading values from RuntimeContext
 */
sealed class GetValError : Exception()

/**
 * Value for given ValBind key not found in values map
 */
object ValBindNotFound : GetValError()

/**
 * Failed to load required value type from json object
 */
object InvalidValBindType : GetValError()

/**
 * App execution context tree.
 * Contains bound values and exposes methods to retrieve them
 */
data class RuntimeContext(private val values: Map<ValBind, Any> = mapOf(), val prev: RuntimeContext? = null) {
    /**
     * Get value from values map
     * @returns Result.Failure if value not found in map or at given path
     */
    private fun <T : Any> getValue(spec: ValSpec): Result<T, GetValError> {
        val obj = values[spec.key]

        return if (obj != null) {
            Result
                .of {
                    spec.path.read<T>(obj)
                }
                .mapError {
                    InvalidValBindType
                }
        } else {
            Result.Failure(ValBindNotFound)
        }
    }

    /**
     * Returns String value for given ValSpec
     * Succeeds for String, Int and Double values, fails for other types
     */
    fun getString(spec: ValSpec): Result<String, GetValError> {
        return this.getValue<Any>(spec)
            .map { s ->
                when (s) {
                    is String -> s
                    is Int -> s.toString()
                    is Double -> s.toString()
                    else -> throw InvalidValBindType
                }
            }
    }

    /**
     * Returns Iterable<T> value at given ValSpec
     */
    fun <T> getIterable(spec: ValSpec): Result<Iterable<T>, GetValError> {
        val obj = values[spec.key]

        return if (obj != null) {
            Result
                .of {
                    spec.path.read<Iterable<T>>(obj)
                }
                .mapError {
                    InvalidValBindType
                }
        } else {
            return Result.Failure(ValBindNotFound)
        }
    }

    fun renderStringTpl(tpl: StringTpl): Result<String, GetValError> {
        return tpl.parts
            .fold(
                Result.Success(StringBuilder()),
                fun(acc, part): Result<StringBuilder, GetValError> {
                    return when (part) {
                        is ConstStrPart -> acc.map { it.append(part.str) }
                        is ValSpecPart -> acc.flatMap { builder -> getString(part.ref).map { v -> builder.append(v) } }
                    }
                }
            )
            .map {
                it.toString()
            }
    }
}

/**
 * ADT defining runtime errors
 */
sealed class RuntimeError : Exception()

object ValueNotFound : RuntimeError()

/**
 * Executes [fetch] command, returns new context with bound value
 */
private suspend fun fetch(fetch: Fetch, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    println("execute fetch: $fetch")
    // 1. resolve fetch url from current context
    // 2. download and parse json from url
    // 3. build new context using new tpl
    val newCtx = RuntimeContext(mapOf(), ctx)
    return statements(fetch.statements, newCtx)
}

/**
 * Executes [download] command, returns nothing
 */
private suspend fun download(download: Download, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    println("execute download: $download")
    // 1. resolve download url and file saving path
    // 2. download content
    return Result.Success(Unit)
}

/**
 * Executes [foreach] command, returns nothing
 */
private suspend fun foreach(foreach: Foreach, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    println("execute foreach: $foreach")
    // 1. resolve iterable from current context
    // 2. for each element - build new ctx and execute statements using it
    return statements(foreach.statements, ctx)
}

/**
 * Executes all given [statements], returns nothing
 */
private suspend fun statements(statements: Statements, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    println("execute statements: $statements")
    // 1. execute all statements in parallel using current ctx
    // 2. check for failed results to stop execution
    for (s in statements) {
        execute(s, ctx)
    }

    return Result.Success(Unit)
}

/**
 * Prints debug message
 */
private fun debug(dbg: Debug, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    return ctx.renderStringTpl(dbg.message)
        .map {
            println(it)
        }
        .mapError {
            ValueNotFound
        }
}

/**
 * Executes [script] within runtime context [ctx]
 */
suspend fun execute(script: HitSyntax, ctx: RuntimeContext): Result<Unit, RuntimeError> = when (script) {
    is Fetch -> fetch(script, ctx)
    is Download -> download(script, ctx)
    is Foreach -> foreach(script, ctx)
    is Statements -> statements(script, ctx)
    is Debug -> debug(script, ctx)
}
