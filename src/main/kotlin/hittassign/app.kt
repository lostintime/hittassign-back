package hittassign

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.mapError
import hittassign.runtime.RuntimeContext
import hittassign.runtime.RuntimeError
import hittassign.runtime.execute
import kotlinx.coroutines.experimental.*
import kotlin.system.exitProcess
import com.xenomachina.argparser.ArgParser
import hittassign.dsl.*
import java.io.File

class AppArgs(parser: ArgParser) {
    val sources by parser.positionalList("script filenames", 0..Int.MAX_VALUE)
}

sealed class AppError : Exception() {
    data class SourceReadFailed(val reason: Exception) : AppError()

    data class LexFailed(val reason: LexError) : AppError()

    data class ParserFailed(val reason: ParseError) : AppError()

    data class RuntimeFailed(val reason: RuntimeError) : AppError()
}

/**
 * Returns a nice error message
 */
fun AppError.getMessage(): String = when (this) {
    is AppError.SourceReadFailed -> "Failed to read source file"
    is AppError.LexFailed -> "Syntax error, please check file idents"
    is AppError.ParserFailed -> "Syntax error: ${this.reason.msg}"
    is AppError.RuntimeFailed -> when (this.reason) {
        is RuntimeError.ValueNotFound -> "No value found with name \"${this.reason.name}\""
        is RuntimeError.InvalidValueType -> "Value missing or wrong type for variable \"${this.reason.name}\" at \"${this.reason.path}\""
        is RuntimeError.FetchFailed -> "Failed to fetch from \"${this.reason.source}\" to \"${this.reason.name}\""
        is RuntimeError.DownloadFailed -> "Failed to download from \"${this.reason.source}\" to \"${this.reason.dest}\""
        is RuntimeError.MkdirFailed -> "Failed to create directory at \"${this.reason.path}\""
    }
}

private suspend fun run(script: String): Result<Unit, AppError> {
    return lex(script)
        .mapError { AppError.LexFailed(it) }
        .flatMap { parse(it).mapError { AppError.ParserFailed(it) } }
        .fold({
            execute(it, RuntimeContext()).mapError { AppError.RuntimeFailed(it) }
        }, {
            Result.Failure(it)
        })
}

fun main(args: Array<String>) = runBlocking<Unit> {
    ArgParser(args).parseInto(::AppArgs).run {
        if (sources.isEmpty()) {
            // process stdin
            Result
                .of {
                    System.`in`.bufferedReader().use {
                        it.readText()
                    }
                }
                .mapError { AppError.SourceReadFailed(it) }
                .fold({ run(it) }, { Result.Failure<Unit, AppError>(it) })
                .fold({}, {
                    println(it.getMessage())
                    println("$it")
                    exitProcess(1)
                })

        } else {
            sources.forEach { source ->
                val job = async {
                    Result
                        .of { File(source).readText() }
                        .mapError { AppError.SourceReadFailed(it) }
                        .fold({ run(it) }, { Result.Failure<Unit, AppError>(it) })
                }

                job.await().fold({}, {
                    println(it.getMessage())
                    println("$it")
                    exitProcess(1)
                })
            }
        }

        exitProcess(0)
    }
}
