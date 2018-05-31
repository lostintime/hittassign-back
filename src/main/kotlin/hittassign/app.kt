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
import hittassign.dsl.lex
import hittassign.dsl.parse
import java.io.File

class AppArgs(parser: ArgParser) {
    val sources by parser.positionalList("script filenames", 0..Int.MAX_VALUE)
}

sealed class AppError : Exception() {
    data class ParseError(val e: Exception) : AppError()

    data class ExecuteError(val e: RuntimeError) : AppError()
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
                .flatMap { lex(it) }
                .mapError { AppError.ParseError(Exception("Lexing failed")) }
                .flatMap { parse(it) }
                .fold({
                    execute(it, RuntimeContext()).mapError { AppError.ExecuteError(it) }
                }, {
                    Result.Failure<Unit, AppError>(AppError.ParseError(it))
                })
        } else {
            sources.forEach { source ->
                val job = async {
                    Result
                        .of { File(source).readText() }
                        .flatMap { lex(it) }
                        .mapError { AppError.ParseError(Exception("Lexing failed")) }
                        .flatMap { parse(it) }
                        .fold({
                            execute(it, RuntimeContext()).mapError { AppError.ExecuteError(it) }
                        }, {
                            Result.Failure<Unit, AppError>(AppError.ParseError(it))
                        })
                }

                job.await().fold({}, {
                    println("Error: $it")
                    exitProcess(1)
                })
            }
        }

        exitProcess(0)
    }
}
