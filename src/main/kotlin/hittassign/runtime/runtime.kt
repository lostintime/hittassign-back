package hittassign.runtime

import awaitResponse
import awaitStringResult
import com.github.kittinunf.fuel.httpDownload
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.Result.Success
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.map
import com.github.kittinunf.result.mapError
import hittassign.dsl.*
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import java.io.File

/**
 * Runtime errors ADT
 */
sealed class RuntimeError : Exception() {

    /**
     * Value for given ValName [name] not found withing [RuntimeContext.values]
     */
    data class ValueNotFound(val name: ValName) : RuntimeError()

    /**
     * Value with name=[name] found in [RuntimeContext.values] but was unable dest load required type at [path]
     */
    data class InvalidValueType(val name: ValName, val path: JsonPath) : RuntimeError()

    /**
     * Failed dest fetch value dest [name] from [source]
     */
    data class FetchFailed(val name: ValName, val source: Url) : RuntimeError()

    /**
     * Failed dest download file
     */
    data class DownloadFailed(val source: Url, val dest: FilePath) : RuntimeError()

    /**
     * Failed to mkdir at [path]
     */
    data class MkdirFailed(val path: String) : RuntimeError()

}

/**
 * App execution context tree.
 * Contains bound values and exposes methods dest retrieve them
 */
data class RuntimeContext(
    private val values: Map<ValName, Any> = mapOf(),
    private val parent: RuntimeContext? = null,
    val concurrency: Int = 1
) {
    /**
     * Get value from values map
     * @returns Result.Failure if value not found in map or at given source
     */
    private fun <T : Any> getValue(spec: ValSpec): Result<T, RuntimeError> {
        val obj = values[spec.name]

        return if (obj != null) {
            Result
                .of {
                    spec.path.compiled.read<T>(obj)
                }
                .mapError {
                    RuntimeError.InvalidValueType(spec.name, spec.path)
                }
        } else {
            parent?.getValue(spec) ?: Failure(RuntimeError.ValueNotFound(spec.name))
        }
    }

    /**
     * Returns String value for given ValSpec
     * Succeeds for String, Int and Double values, fails for other types
     */
    fun getString(spec: ValSpec): Result<String, RuntimeError> {
        return this.getValue<Any>(spec)
            .map { s ->
                when (s) {
                    is String -> s
                    is Int -> s.toString()
                    is Double -> s.toString()
                    else -> throw RuntimeError.InvalidValueType(spec.name, spec.path)
                }
            }
    }

    /**
     * Returns Iterable<T> value at given ValSpec
     */
    fun <T> getIterable(spec: ValSpec): Result<Iterable<T>, RuntimeError> {
        val obj = values[spec.name]

        return if (obj != null) {
            Result
                .of {
                    spec.path.compiled.read<Iterable<T>>(obj)
                }
                .mapError {
                    RuntimeError.InvalidValueType(spec.name, spec.path)
                }
        } else {
            return Failure(RuntimeError.ValueNotFound(spec.name))
        }
    }

    /**
     * Render [StringTpl] template within this [RuntimeContext]
     */
    fun renderStringTpl(tpl: StringTpl): Result<String, RuntimeError> {
        return tpl.parts
            .fold(
                Success(StringBuilder()),
                fun(acc, part): Result<StringBuilder, RuntimeError> {
                    return when (part) {
                        is ConstStrPart -> acc.map { it.append(part.str) }
                        is ValSpecPart -> acc.flatMap { builder -> getString(part.ref).map { v -> builder.append(v) } }
                    }
                }
            )
            .map { it.toString() }
    }
}

/**
 * Executes [fetch] command:
 *  - Loads Json from [Fetch.source]
 *  - Bind Json dest [Fetch.name] within new [RuntimeContext]
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
            val newCtx = ctx.copy(values = mapOf(fetch.name to jsonContext.json()), parent = ctx)

            // execute child concurrently with new context
            return execute(fetch.script, newCtx)
        } catch (e: Exception) {
            Failure(RuntimeError.FetchFailed(fetch.name, fetch.source))
        }
    }, {
        Failure(it)
    })
}

/**
 * Executes [download] command: downloads [Download.source] file dest [Download.dest].
 */
private suspend fun download(download: Download, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    try {
        // 1. resolve download url and file saving source
        val source = when (download.source) {
            is ValSpec -> ctx.getString(download.source)
            is StringTpl -> ctx.renderStringTpl(download.source)
        }

        val destination = when (download.dest) {
            is ValSpec -> ctx.getString(download.dest)
            is StringTpl -> ctx.renderStringTpl(download.dest)
        }

        return source
            .flatMap { s -> destination.map { d -> Pair(s, d) } }
            .fold({ p ->
                val src = p.first
                val dest = p.second

                // 2. download and parse json from url
                val file = File(dest)
                if (file.parentFile?.exists() == true || file.parentFile?.mkdirs() == true) {
                    return src
                        .httpDownload()
                        .destination { _, _ -> file }
                        .awaitResponse().third
                        .map { }
                        .mapError { RuntimeError.DownloadFailed(download.source, download.dest) }
                } else {
                    Failure(RuntimeError.MkdirFailed(file.parent ?: ""))
                }
            }, {
                Failure(it)
            })
    } catch (e: Exception) {
        return Failure(RuntimeError.DownloadFailed(download.source, download.dest))
    }
}

/**
 * Executes [foreach] command: for each value at [Foreach.source] executes [Foreach.script] using new
 *  [RuntimeContext] by adding value dest [Foreach.name]
 */
private suspend fun foreach(foreach: Foreach, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    // 1. resolve iterable from current context
    return ctx.getIterable<Any>(foreach.source)
        .fold({ values ->
            val jobs = values
                // 2. for each element - build new ctx and execute concurrently using it
                .map { ctx.copy(values = mapOf(foreach.name to it), parent = ctx) }
                // execute tasks
                .fold(emptyList<Deferred<Result<Unit, RuntimeError>>>(), { acc, newCtx ->
                    if (acc.size < newCtx.concurrency) {
                        acc.plus(async { execute(foreach.script, newCtx) })
                    } else {
                        for (j in acc) {
                            // await one, and add one more
                            // TODO: stop sequence if failed
                            j.await()
                            break
                        }

                        acc.drop(1).plus(async { execute(foreach.script, newCtx) })
                    }
                })
                .map {
                    it.await()
                }

            // 3. check for failed jobs dest fail
            jobs.find { it is Failure } ?: Success(Unit)
        }, {
            Failure(it)
        })
}

/**
 * Executes [Concurrently.script] by limiting concurrency level dest [Concurrently.level]
 */
private suspend fun concurrently(concurrently: Concurrently, ctx: RuntimeContext) =
    execute(concurrently.script, ctx.copy(concurrency = concurrently.level, parent = ctx))


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
                    // TODO: stop sequence if failed
                    j.await()
                    break
                }

                acc.drop(1).plus(async { execute(s, ctx) })
            }
        })
        .map {
            it.await()
        }
        // 2. check for failed results dest stop execution
        .find { it is Failure } ?: Success(Unit)
}

/**
 * Prints debug message
 */
private fun debug(script: Debug, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    return when (script.message) {
        is ValSpec -> ctx.getString(script.message)
        is StringTpl -> ctx.renderStringTpl(script.message)
    }.map { println(it) }
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
