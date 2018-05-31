package hittassign.runtime

import awaitResponse
import awaitStringResult
import com.github.kittinunf.fuel.httpDownload
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.*
import hittassign.dsl.*
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import java.io.File

/**
 * ADT defining errors happening on reading values from RuntimeContext
 */
sealed class GetValError : Exception()

/**
 * Value for given ValBind [key] not found withing [RuntimeContext.values]
 */
data class ValBindNotFound(val key: ValBind) : GetValError()

/**
 * Value with name=[key] found in [RuntimeContext.values] but was unable to load required type at [path]
 */
data class InvalidValBindType(val key: ValBind, val path: JsonPath) : GetValError()

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
                    spec.path.compiled.read<T>(obj)
                }
                .mapError {
                    InvalidValBindType(spec.key, spec.path)
                }
        } else {
            parent?.getValue(spec) ?: Result.Failure(ValBindNotFound(spec.key))
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
                    else -> throw InvalidValBindType(spec.key, spec.path)
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
                    spec.path.compiled.read<Iterable<T>>(obj)
                }
                .mapError {
                    InvalidValBindType(spec.key, spec.path)
                }
        } else {
            return Result.Failure(ValBindNotFound(spec.key))
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

/**
 * Filed to read value from [RuntimeContext] ([RuntimeError] type)
 */
data class ValueReadError(val prev: GetValError) : RuntimeError()

/**
 * Failed to fetch value to [key] from [source]
 */
data class ValueFetchError(val key: ValBind, val source: Url) : RuntimeError()

/**
 * Failed to download file
 */
data class FileDownloadError(val source: Url, val to: FilePath) : RuntimeError()

/**
 * Executes [fetch] command:
 *  - Loads Json from [Fetch.source]
 *  - Bind Json to [Fetch.key] within new [RuntimeContext]
 *  - Executes all [Fetch.script] using new [RuntimeContext]
 */
private suspend fun fetch(fetch: Fetch, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    // 1. resolve fetch url from current context
    val source = when (fetch.source) {
        is ValSpec -> ctx.getString(fetch.source)
        is StringTpl -> ctx.renderStringTpl(fetch.source)
    }

    return source.fold({ url ->
        // 2. download and parse json from url
        val jsonStr = url.httpGet().awaitStringResult()

        try {
            // 3. build new context using fetched json
            val jsonContext = JsonPath.parse(jsonStr)
            val newCtx = ctx.copy(values = mapOf(fetch.key to jsonContext.json()))

            // execute child concurrently with new context
            return execute(fetch.script, newCtx)
        } catch (e: Exception) {
            Result.Failure(ValueFetchError(fetch.key, fetch.source))
        }
    }, {
        Result.Failure(ValueReadError(it))
    })
}

/**
 * Executes [download] command: downloads [Download.source] file to [Download.to].
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
                    .mapError { FileDownloadError(download.source, download.to) }
            }, {
                Result.Failure(ValueReadError(it))
            })
    } catch (e: Exception) {
        return Result.Failure(FileDownloadError(download.source, download.to))
    }
}

/**
 * Executes [foreach] command: for each value at [Foreach.source] executes [Foreach.script] using new
 *  [RuntimeContext] by adding value to [Foreach.key]
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
                        acc.plus(async { execute(foreach.script, newCtx) })
                    } else {
                        val job = acc.firstOrNull()
                        if (job != null) {
                            // await first task
                            // TODO: stop execution if job failed, cancel already running jobs
                            job.await()
                            acc.drop(1).plus(async { execute(foreach.script, newCtx) })
                        } else {
                            acc.plus(async { execute(foreach.script, newCtx) })
                        }
                    }
                })
                .map {
                    it.await()
                }

            // 3. check for failed jobs to fail
            jobs.find { it is Result.Failure } ?: Result.Success(Unit)
        }, {
            Result.Failure(ValueReadError(it))
        })
}

/**
 * Executes [Concurrently.script] by limiting concurrency level to [Concurrently.level]
 */
private suspend fun concurrently(concurrently: Concurrently, ctx: RuntimeContext) =
    execute(concurrently.script, ctx.copy(concurrency = concurrently.level))


/**
 * Executes all [list] script concurrently
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
}

/**
 * Prints debug message
 */
private fun debug(script: Debug, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    return ctx.renderStringTpl(script.message)
        .map { println(it) }
        .mapError { ValueReadError(it) }
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
