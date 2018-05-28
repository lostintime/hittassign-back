package hittassign

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.mapError
import hittassign.dsl.lex
import hittassign.dsl.parse
import hittassign.runtime.RuntimeContext
import hittassign.runtime.RuntimeError
import hittassign.runtime.execute
import kotlinx.coroutines.experimental.*
import kotlin.system.exitProcess

sealed class AppError : Exception() {
    data class ParseError(val e: Exception) : AppError()

    data class ExecuteError(val e: RuntimeError) : AppError()
}

fun main(args: Array<String>) = runBlocking<Unit> {
    val job = async {
        // TODO: load script from source file or stdin
        lex("")
            .flatMap { parse(it) }
            .fold({
                execute(it, RuntimeContext()).mapError { AppError.ExecuteError(it) }
            }, {
                Result.Failure<Unit, AppError>(AppError.ParseError(it))
            })
    }

    job.await().fold({
        exitProcess(0)
    }, {
        println("Error: $it")
        exitProcess(1)
    })
}
