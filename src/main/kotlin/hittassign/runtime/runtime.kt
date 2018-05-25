package hittassign.runtime

import com.github.kittinunf.result.Result
import hittassign.dsl.*

data class RuntimeContext(val prev: RuntimeContext? = null)

sealed class RuntimeError : Exception()

private suspend fun fetch(fetch: Fetch, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    println("execute fetch: $fetch")
    // 1. resolve fetch url from current context
    // 2. download and parse json from url
    // 3. build new context using new tpl
    val newCtx = RuntimeContext(ctx)
    return statements(fetch.statements, newCtx)
}

private suspend fun download(download: Download, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    println("execute download: $download")
    // 1. resolve download url and file saving path
    // 2. download content
    return Result.Success(Unit)
}

private suspend fun foreach(foreach: Foreach, ctx: RuntimeContext): Result<Unit, RuntimeError> {
    println("execute foreach: $foreach")
    // 1. resolve iterable from current context
    // 2. for each element - build new ctx and execute statements using it
    return statements(foreach.statements, ctx)
}

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
 * Executes [script] within runtime context [ctx]
 */
suspend fun execute(script: HitSyntax, ctx: RuntimeContext): Result<Unit, RuntimeError> = when (script) {
    is Fetch -> fetch(script, ctx)
    is Download -> download(script, ctx)
    is Foreach -> foreach(script, ctx)
    is Statements -> statements(script, ctx)
}