package hittassign.runtime

import awaitResponse
import awaitStringResult
import com.github.kittinunf.fuel.httpDownload
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.*
import com.jayway.jsonpath.JsonPath
import hittassign.dsl.*
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import java.io.File

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
data class RuntimeContext(
    private val values: Map<ValBind, Any> = mapOf(),
    private val parent: RuntimeContext? = null,
    val concurrency: Int = 32 // just a magic number
) {
    /**
     * Get value from values map
     * @returns Result.Failure if value not found in map or at given source
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
            parent?.getValue(spec) ?: Result.Failure(ValBindNotFound)
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

object FetchFailed : RuntimeError()

object DownloadFailed : RuntimeError()

/**
 * Executes [fetch] command, returns new context with bound value
 */
private suspend fun fetch(fetch: Fetch, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    // 1. resolve fetch url from current context
    val source = when (fetch.source) {
        is ValSpec -> ctx.getString(fetch.source)
        is StringTpl -> ctx.renderStringTpl(fetch.source)
    }

    return source.fold({ url ->
        // 2. download and parse json from url
        // println("fetch.url: $url")
        val jsonStr = url.httpGet().awaitStringResult()

        try {
            // 3. build new context using fetched json
            val jsonContext = JsonPath.parse(jsonStr)
            // println("fetch.json: ${jsonContext.jsonString()}")
            val newCtx = ctx.copy(values = mapOf(fetch.key to jsonContext.json()))
            // println("fetch.newCtx: $newCtx")
            // execute child concurrently with new context
            return statements(fetch.statements, newCtx)
        } catch (e: Exception) {
            // println(e)
            Result.Failure(FetchFailed)
        }
    }, {
        Result.Failure(ValueNotFound)
    })
}

/**
 * Executes [download] command, returns nothing
 */
private suspend fun download(download: Download, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    try {
        // 1. resolve download url and file saving source
        val source = when (download.source) {
            is ValSpec -> ctx.getString(download.source)
            is StringTpl -> ctx.renderStringTpl(download.source)
        }

        val destination = when (download.to) {
            is ValSpec -> ctx.getString(download.to)
            is StringTpl -> ctx.renderStringTpl(download.to)
        }

        return source
            .flatMap { s -> destination.map { d -> Pair(s, d) } }
            .fold({ p ->
                val src = p.first
                val dest = p.second

                // 2. download and parse json from url
                val result = src
                    .httpDownload()
                    .destination { _, _ -> File(dest) }
                    .awaitResponse()

                return result.third
                    .map { }
                    .mapError { DownloadFailed }
            }, {
                Result.Failure(ValueNotFound)
            })
    } catch (e: Exception) {
        return Result.Failure(DownloadFailed)
    }
}

/**
 * Executes [foreach] command, returns nothing
 */
private suspend fun foreach(foreach: Foreach, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    // 1. resolve iterable from current context
    return ctx.getIterable<Any>(foreach.source)
        .fold({ values ->
            val jobs = values
                // 2. for each element - build new ctx and execute concurrently using it
                .map { ctx.copy(values = mapOf(foreach.key to it)) }
                // execute tasks
                .fold(emptyList<Deferred<Result<Unit, RuntimeError>>>(), { acc, newCtx ->
                    if (acc.size < newCtx.concurrency) {
                        acc.plus(async { statements(foreach.statements, newCtx) })
                    } else {
                        val job = acc.firstOrNull()
                        if (job != null) {
                            // await first task
                            job.await()
                            // FIXME: stop all chain if task failed
                            acc.drop(1).plus(async { statements(foreach.statements, newCtx) })
                        } else {
                            acc.plus(async { statements(foreach.statements, newCtx) })
                        }
                    }
                })
                .map {
                    it.await()
                }

            // 3. check for failed jobs to fail
            jobs.find { it is Result.Failure } ?: Result.Success(Unit)
        }, {
            Result.Failure(ValueNotFound)
        })
}

private suspend fun concurrently(script: Concurrently, ctx: RuntimeContext) =
    statements(script, ctx.copy(concurrency = script.level))


/**
 * Executes all [list] concurrently, returns nothing
 */
private suspend fun statements(list: List<HitSyntax>, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    // 1. execute all concurrently in parallel using current ctx
    return list
        .fold(emptyList<Deferred<Result<Unit, RuntimeError>>>(), { acc, s ->
            if (acc.size < ctx.concurrency) {
                acc.plus(async { execute(s, ctx) })
            } else {
                for (j in acc) {
                    // await one, and add one more
                    // FIXME: stop sequence if failed
                    j.await()
                    break
                }

                acc.plus(async { execute(s, ctx) })
            }
        })
        .map {
            it.await()
        }
        // 2. check for failed results to stop execution
        .find { it is Result.Failure } ?: Result.Success(Unit)

//    // 1. execute all concurrently in parallel using current ctx
//    return list
//        .map {
//            async { execute(it, ctx) }
//        }
//        .map {
//            it.await()
//        }
//        // 2. check for failed results to stop execution
//        .find { it is Result.Failure } ?: Result.Success(Unit)
}

/**
 * Prints debug message
 */
private fun debug(script: Debug, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    return ctx.renderStringTpl(script.message)
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
    is Concurrently -> concurrently(script, ctx)
    is Statements -> statements(script, ctx)
    is Debug -> debug(script, ctx)
}
