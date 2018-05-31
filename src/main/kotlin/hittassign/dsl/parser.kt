package hittassign.dsl

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.map
import com.github.kittinunf.result.mapError
import com.jayway.jsonpath.JsonPath

data class ParseError(val msg: String) : Exception()

fun isValidValKey(key: String): Boolean = Regex("""^[a-zA-Z_${'$'}][a-zA-Z0-9_${'$'}]*$""").matches(key)

/**
 * Invalid ValSpec value error
 */
object InvalidValPath : Exception()

/**
 * Partial parse result
 */
data class Parsed(val script: HitSyntax, val tail: List<HitLexeme>)

typealias ParseResult = Result<Parsed, ParseError>

private fun valBind(s: String): Result<ValBind, ParseError> {
    return Success(ValBind(s))
}

/**
 * Parse ValRef from string
 */
private fun valRef(s: String): Result<ValRef, ParseError> {
    return stringTpl(s)
}

private fun valSpec(s: String): Result<ValSpec, ParseError> {
    return Result.Success(ValSpec(ValBind(s), JsonPath.compile("$")))
}

private fun stringTpl(s: String): Result<StringTpl, ParseError> {
    return Success(StringTpl(ConstStrPart(s)))
}

private fun expectSymbol(l: HitLexeme?): Result<HitLexeme.Symbol, ParseError> {
    return when (l) {
        is HitLexeme.Symbol -> Success(l)
        else -> Failure(ParseError("Symbol expected at .."))
    }
}

/**
 * Parse debug statement
 */
private fun debug(lex: List<HitLexeme>): ParseResult {
    return expectSymbol(lex.firstOrNull())
        .flatMap { stringTpl(it.sym) }
        .map { msg ->
            Parsed(Debug(msg), lex.drop(1))
        }
}

/**
 * Parse fetch statement
 */
private fun fetch(lex: List<HitLexeme>): ParseResult {
    return expectSymbol(lex.firstOrNull())
        .flatMap { valBind(it.sym) }
        .flatMap { bind ->
            expectSymbol(lex.drop(1).firstOrNull())
                .flatMap { valRef(it.sym) }
                .flatMap { source ->
                    block(emptyList(), lex.drop(2))
                        .map { block ->
                            Parsed(
                                Fetch(bind, source, block.script),
                                block.tail
                            )
                        }
                }
        }

}

/**
 * Parse foreach statement
 */
private fun foreach(lex: List<HitLexeme>): ParseResult {
    return expectSymbol(lex.firstOrNull())
        .flatMap { valBind(it.sym) }
        .flatMap { bind ->
            expectSymbol(lex.drop(1).firstOrNull())
                .flatMap { valSpec(it.sym) }
                .flatMap { source ->
                    block(emptyList(), lex.drop(2))
                        .map { block ->
                            Parsed(
                                Foreach(bind, source, block.script),
                                block.tail
                            )
                        }
                }
        }
}

/**
 * Parse download statement
 */
private fun download(lex: List<HitLexeme>): ParseResult {
    return expectSymbol(lex.firstOrNull())
        .flatMap { valRef(it.sym) }
        .flatMap { src ->
            expectSymbol(lex.drop(1).firstOrNull())
                .flatMap { valRef(it.sym) }
                .map { dest ->
                    Parsed(
                        Download(src, dest),
                        lex.drop(2)
                    )
                }
        }
}

/**
 * Set concurrencly level for child block
 */
private fun concurrently(lex: List<HitLexeme>): ParseResult {
    return expectSymbol(lex.firstOrNull())
        .flatMap {
            val n: Int? = it.sym.toIntOrNull()

            if (n != null) {
                block(emptyList(), lex.drop(1))
                    .map { block ->
                        Parsed(
                            Concurrently(n, block.script),
                            block.tail
                        )
                    }
            } else {
                Failure(ParseError("Integer expected at .."))
            }
        }
}

/**
 * Parses block content until Dedent or EOF
 */
tailrec fun block(script: List<HitSyntax>, lex: List<HitLexeme>): ParseResult {
    return if (lex.isEmpty()) {
        Success(Parsed(Statements(script), emptyList()))
    } else {
        val head = lex.firstOrNull()
        val tail = lex.drop(1)

        when (head) {
            null -> Success(Parsed(Statements(script), emptyList()))
            is HitLexeme.Symbol -> {
                val s = when (head.sym.toLowerCase()) {
                    "debug" -> debug(tail)
                    "fetch" -> fetch(tail)
                    "foreach" -> foreach(tail)
                    "download" -> download(tail)
                    "concurrently" -> concurrently(tail)
                    else -> Failure(ParseError("Unknown symbol \"$head.sym\"")) //
                }

                return when (s) {
                    is Success -> block(script + s.value.script, s.value.tail)
                    else -> s
                }
            }
            HitLexeme.Ident -> Failure(ParseError("Unexpected Ident at .."))
            HitLexeme.Dedent -> Success(Parsed(Statements(script), tail))
        }
    }
}

/**
 * Parse input lexemes list [lex] into AST
 */
fun parse(lex: List<HitLexeme>): Result<HitSyntax, ParseError> {
    return block(emptyList(), lex).map { it.script }
}

fun demo(): Result<HitSyntax, ParseError> {
    // TODO implement me
    val basePath = "/Users/vadim/projects/lostintime/hittassign-back/tmp/"

    return Success(
        Concurrently(
            2,
            Statements(
                Debug(StringTpl(ConstStrPart("Hello there!!!! this is debug message"))),
                Fetch(
                    ValBind("h"),
                    StringTpl(ConstStrPart("https://api.hitta.se/search/v7/app/combined/within/57.840703831916%3A11.728156448084002%2C57.66073920808401%3A11.908121071915998/?range.to=101&range.from=1&geo.hint=57.75072152%3A11.81813876&sort.order=relevance&query=lunch")),
                    Statements(
                        Foreach(
                            ValBind("c"),
                            ValSpec(ValBind("h"), JsonPath.compile("$.result.companies.company")),
                            Statements(
                                Debug(
                                    StringTpl(
                                        ConstStrPart("Company: "),
                                        ValSpecPart(ValSpec(ValBind("c"), JsonPath.compile("$.id")))
                                    )
                                ),
                                Download(
                                    StringTpl(
                                        // ConstStrPart("https://lostintimedev.com/about/")
                                        ConstStrPart("https://www.hitta.se/")
                                    ),
                                    StringTpl(
                                        ConstStrPart(basePath),
                                        ValSpecPart(ValSpec(ValBind("c"), JsonPath.compile("$.id"))),
                                        ConstStrPart(".html")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}

fun valPath(path: String): Result<ValSpec, InvalidValPath> {
    val firstDot = path.indexOf('.')
    val key = if (firstDot >= 0) path.substring(0, firstDot) else path
    val jsonPath = if (firstDot >= 0) "\$${path.substring(firstDot)}" else "$"

    return if (isValidValKey(key)) {
        Result
            .of { JsonPath.compile(jsonPath) }
            .mapError { InvalidValPath }
            .map { ValSpec(ValBind(key), it) }
    } else {
        Failure(InvalidValPath)
    }
}


