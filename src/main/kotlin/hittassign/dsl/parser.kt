package hittassign.dsl

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.map
import com.github.kittinunf.result.mapError
import com.jayway.jsonpath.JsonPath

data class ParseError(val msg: String) : Exception()

fun isValidValKey(key: String): Boolean = Regex("""^[a-zA-Z_${'$'}][a-zA-Z0-9_${'$'}]*$""").matches(key)

/**
 * Invalid ValSpec value error
 */
object InvalidValPath : Exception()

sealed class ParseResult {
    data class Success(val script: HitSyntax, val tail: List<HitLexeme>) : ParseResult()

    object Failure : ParseResult()
}

private fun valBind(s: String): Result<ValBind, ParseError> {
    return Result.Success(ValBind(s))
}

/**
 * Parse ValRef from string
 */
private fun valRef(s: String): Result<ValRef, ParseError> {
    return stringTpl(s)
}

private fun stringTpl(s: String): Result<StringTpl, ParseError> {
    return Result.Success(StringTpl(ConstStrPart(s)))
}

private fun debug(lex: List<HitLexeme>): ParseResult {
    val msg = lex.firstOrNull()

    return when (msg) {
        is HitLexeme.Symbol -> {
            val m = stringTpl(msg.sym)

            when (m) {
                is Result.Success -> ParseResult.Success(Debug(m.value), lex.drop(1))
                is Result.Failure -> ParseResult.Failure // Failed to parse string pl
            }
        }
        else -> ParseResult.Failure // Symbol expected at ..
    }
}

private fun fetch(lex: List<HitLexeme>): ParseResult {
    val bind = lex.firstOrNull()
    return when (bind) {
        is HitLexeme.Symbol -> {
            val name = valBind(bind.sym)
            when (name) {
                is Result.Success -> {
                    val source = lex.drop(1).firstOrNull()
                    when (source) {
                        is HitLexeme.Symbol -> {
                            val url = stringTpl(source.sym)
                            when (url) {
                                is Result.Success -> {
                                    val body = block(emptyList(), lex.drop(2))
                                    when (body) {
                                        is ParseResult.Success -> ParseResult.Success(
                                            Fetch(name.value, url.value, body.script),
                                            body.tail
                                        )
                                        is ParseResult.Failure -> body
                                    }
                                }
                                is Result.Failure -> ParseResult.Failure // ValSpec
                            }
                        }
                        else -> ParseResult.Failure // Symbol expected at ..
                    }
                }
                is Result.Failure -> ParseResult.Failure
            }
        }
        else -> ParseResult.Failure // Symbol expected at ..
    }
}

private fun foreach(lex: List<HitLexeme>): ParseResult {
    TODO("not implemented")
}

private fun download(lex: List<HitLexeme>): ParseResult {
    TODO("not implemented")
}

/**
 * Parses block content until Dedent or EOF
 */
tailrec fun block(script: List<HitSyntax>, lex: List<HitLexeme>): ParseResult {
    return if (lex.isEmpty()) {
        ParseResult.Success(Statements(script), emptyList())
    } else {
        val head = lex.firstOrNull()
        val tail = lex.drop(1)

        when (head) {
            null -> ParseResult.Success(Statements(script), emptyList())
            is HitLexeme.Symbol -> {
                val s = when (head.sym.toLowerCase()) {
                    "debug" -> debug(tail)
                    "fetch" -> fetch(tail)
                    "foreach" -> fetch(tail)
                    "download" -> fetch(tail)
                    else -> ParseResult.Failure //
                }

                return when (s) {
                    is ParseResult.Success -> block(script + s.script, s.tail)
                    is ParseResult.Failure -> s
                }
            }
            HitLexeme.Ident -> ParseResult.Failure
            HitLexeme.Dedent -> ParseResult.Success(Statements(script), tail)
        }
    }
}

/**
 * Parse input lexemes list [lex] into AST
 */
fun parse(lex: List<HitLexeme>): Result<HitSyntax, ParseError> {
    val b = block(emptyList(), lex)

    return when (b) {
        is ParseResult.Success -> Result.Success(b.script)
        is ParseResult.Failure -> Result.Failure(ParseError("Something went wrong"))
    }
}

fun demo(): Result<HitSyntax, ParseError> {
    // TODO implement me
    val basePath = "/Users/vadim/projects/lostintime/hittassign-back/tmp/"

    return Result.Success(
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
        Result.Failure(InvalidValPath)
    }
}


