package hittassign.dsl

import com.github.kittinunf.result.*
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success

/**
 * Partial parse result
 */
data class Parsed(val script: HitSyntax, val tail: List<HitLexeme>)

/**
 * Parse error type
 */
data class ParseError(val msg: String) : Exception()

typealias ParseResult = Result<Parsed, ParseError>

/**
 * Returns true if key is a valid value name
 */
fun isValidValName(key: String): Boolean = Regex("""^[a-zA-Z_${'$'}][a-zA-Z0-9_${'$'}]*$""").matches(key)

internal fun valBind(s: String): Result<ValBind, ParseError> {
    return Success(ValBind(s))
}

/**
 * Parse string [s] to [ValRef]: first check for a valid [ValRef] then [StringTpl]
 */
internal fun valRef(s: String): Result<ValRef, ParseError> {
    return valSpec(s)
        .flatMapError { stringTpl(s) }
        .mapError { ParseError("Invalid ValRef at ..") }
}

/**
 * Parses [spec] string to [ValSpec]
 */
internal fun valSpec(spec: String): Result<ValSpec, ParseError> {
    val firstDot = spec.indexOf('.')
    val name = if (firstDot >= 0) spec.substring(0, firstDot) else spec
    val jsonPath = if (firstDot >= 0) "\$${spec.substring(firstDot)}" else "$"

    return if (isValidValName(name)) {
        Result
            .of { JsonPath.compile(jsonPath) }
            .mapError { ParseError("Invalid Json Path at ..") }
            .map { ValSpec(ValBind(name), it) }
    } else {
        Failure(ParseError("Invalid val spec \"$spec\" at .."))
    }
}

private sealed class StringTplParser {

    abstract fun parse(c: Char): StringTplParser

    abstract fun finish(): StringTplParser

    data class Success(val tpl: StringTpl) : StringTplParser() {
        override fun parse(c: Char): StringTplParser {
            return when (c) {
                '{' -> OnValRef("", tpl)
                '}' -> Invalid // Unexpected '}' at ..
                else -> OnConstStr(Character.toString(c), tpl)
            }
        }

        override fun finish(): StringTplParser = this
    }

    data class OnConstStr(val str: String, val tpl: StringTpl) : StringTplParser() {
        override fun parse(c: Char): StringTplParser {
            return when (c) {
                '{' -> if (str.lastOrNull() == '\\') {
                    OnConstStr(str.dropLast(1) + c, tpl)
                } else {
                    OnValRef("", tpl.copy(parts = tpl.parts + ConstStrPart(str)))
                }
                '}' -> if (str.lastOrNull() == '\\') {
                    OnConstStr(str.dropLast(1) + c, tpl)
                } else {
                    Invalid // Unexpected '}' at ..
                }
                else -> OnConstStr(str + c, tpl)
            }
        }

        override fun finish(): StringTplParser = Success(tpl.copy(parts = tpl.parts + ConstStrPart(str)))
    }

    data class OnValRef(val spec: String, val tpl: StringTpl) : StringTplParser() {
        override fun parse(c: Char): StringTplParser {
            return when (c) {
                '}' -> {
                    if (spec.lastOrNull() == '\\') {
                        OnValRef(spec.dropLast(1) + c, tpl)
                    } else {
                        val v = valSpec(spec)

                        if (v is Result.Success) {
                            Success(tpl.copy(parts = tpl.parts + ValSpecPart(v.value)))
                        } else {
                            Invalid // Invalid ValSpec at ..
                        }
                    }
                }
                else -> OnValRef(spec + c, tpl)
            }
        }

        override fun finish(): StringTplParser = Invalid // referenced values must be enclosed with '{', '}'
    }

    object Invalid : StringTplParser() {
        override fun parse(c: Char): StringTplParser = this

        override fun finish(): StringTplParser = this
    }

    companion object {
        val Empty = Success(StringTpl())
    }
}


/**
 * Parses [StringTpl] from string [s]
 */
internal fun stringTpl(s: String): Result<StringTpl, ParseError> {
    val result = s.fold<StringTplParser>(StringTplParser.Empty, { acc, c -> acc.parse(c) }).finish()

    return when (result) {
        is StringTplParser.Success -> Success(result.tpl)
        else -> Failure(ParseError("Invalid StringTpl at .."))
    }
}

/**
 * Validates [l] is a [HitLexeme.Symbol] or fails with a [ParseError]
 */
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
