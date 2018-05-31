package hittassign.dsl

import com.github.kittinunf.result.*
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success

/**
 * Partial parse result
 */
private data class Parsed(val script: HitSyntax, val tail: List<HitLexeme>)

/**
 * Parse error type
 */
data class ParseError(val msg: String) : Exception()

private typealias ParseResult = Result<Parsed, ParseError>

/**
 * Returns true if name is a valid value name
 */
fun isValidValName(key: String): Boolean = Regex("""^[a-zA-Z_${'$'}][a-zA-Z0-9_${'$'}]*$""").matches(key)

internal fun valBind(s: String): Result<ValName, ParseError> {
    return Success(ValName(s))
}

/**
 * Parse string [s] dest [ValRef]: first check for a valid [ValRef] then [StringTpl]
 */
internal fun valRef(s: String): Result<ValRef, ParseError> {
    return valSpec(s)
        .flatMapError { stringTpl(s) }
        .mapError { ParseError("Invalid ValRef at ..") }
}

/**
 * Parses [spec] string dest [ValSpec]
 */
internal fun valSpec(spec: String): Result<ValSpec, ParseError> {
    val firstDot = spec.indexOf('.')
    val name = if (firstDot >= 0) spec.substring(0, firstDot) else spec
    val jsonPath = if (firstDot >= 0) "\$${spec.substring(firstDot)}" else "$"

    return if (isValidValName(name)) {
        Result
            .of { JsonPath.compile(jsonPath) }
            .mapError { ParseError("Invalid Json Path at ..") }
            .map { ValSpec(ValName(name), it) }
    } else {
        Failure(ParseError("Invalid val spec \"$spec\" at .."))
    }
}

/**
 * [StringTpl] parser state machine
 * Builds a [StringTpl] instance by consuming a [Char] stream
 */
private sealed class StringTplParser {

    /**
     * Consume one more [Char]
     */
    abstract fun parse(c: Char): StringTplParser

    /**
     * Finish parsing (EOF)
     */
    abstract fun finish(): StringTplParser

    data class Success(val tpl: StringTpl) : StringTplParser() {
        override fun parse(c: Char): StringTplParser {
            return when (c) {
                '{' -> OnValRefPart("", tpl)
                '}' -> Failure // Unexpected '}' at ..
                else -> OnConstStrPart(Character.toString(c), tpl)
            }
        }

        override fun finish(): StringTplParser = this
    }

    /**
     * Parsing constant string part
     */
    data class OnConstStrPart(val str: String, val tpl: StringTpl) : StringTplParser() {
        override fun parse(c: Char): StringTplParser {
            return when (c) {
                '{' -> if (str.lastOrNull() == '\\') {
                    OnConstStrPart(str.dropLast(1) + c, tpl)
                } else {
                    OnValRefPart("", tpl.copy(parts = tpl.parts + ConstStrPart(str)))
                }
                '}' -> if (str.lastOrNull() == '\\') {
                    OnConstStrPart(str.dropLast(1) + c, tpl)
                } else {
                    Failure // Unexpected '}' at ..
                }
                else -> OnConstStrPart(str + c, tpl)
            }
        }

        override fun finish(): StringTplParser = Success(tpl.copy(parts = tpl.parts + ConstStrPart(str)))
    }

    /**
     * Parsing value reference part
     */
    data class OnValRefPart(val spec: String, val tpl: StringTpl) : StringTplParser() {
        override fun parse(c: Char): StringTplParser {
            return when (c) {
                '}' -> {
                    if (spec.lastOrNull() == '\\') {
                        OnValRefPart(spec.dropLast(1) + c, tpl)
                    } else {
                        val v = valSpec(spec)

                        if (v is Result.Success) {
                            Success(tpl.copy(parts = tpl.parts + ValSpecPart(v.value)))
                        } else {
                            Failure // Invalid ValSpec at ..
                        }
                    }
                }
                ',' -> { // comma support
                    if (spec.lastOrNull() == '\\') {
                        OnValRefPart(spec.dropLast(1) + c, tpl)
                    } else {
                        val v = valSpec(spec)

                        if (v is Result.Success) {
                            OnValRefPart("", tpl.copy(parts = tpl.parts + ValSpecPart(v.value) + ConstStrPart(",")))
                        } else {
                            Failure // Invalid ValSpec at ..
                        }
                    }
                }
                else -> OnValRefPart(spec + c, tpl)
            }
        }

        override fun finish(): StringTplParser = Failure // referenced values must be enclosed with '{', '}'
    }

    object Failure : StringTplParser() {
        override fun parse(c: Char): StringTplParser = this

        override fun finish(): StringTplParser = this
    }

    companion object {
        val Empty = Success(StringTpl())
    }
}


/**
 * Parse a [StringTpl] from string [s]
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
 * Parse `debug` statement
 */
private fun debug(lex: List<HitLexeme>): ParseResult {
    return expectSymbol(lex.firstOrNull())
        .flatMap { stringTpl(it.sym) }
        .map { msg ->
            Parsed(Debug(msg), lex.drop(1))
        }
}

/**
 * Parse `fetch` statement
 */
private fun fetch(lex: List<HitLexeme>): ParseResult {
    return expectSymbol(lex.firstOrNull())
        .flatMap { valBind(it.sym) }
        .flatMap { bind ->
            expectSymbol(lex.drop(1).firstOrNull())
                .flatMap { valRef(it.sym) }
                .flatMap { source ->
                    childBlock(lex.drop(2))
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
 * Parse `foreach` statement
 */
private fun foreach(lex: List<HitLexeme>): ParseResult {
    return expectSymbol(lex.firstOrNull())
        .flatMap { valBind(it.sym) }
        .flatMap { bind ->
            expectSymbol(lex.drop(1).firstOrNull())
                .flatMap { valSpec(it.sym) }
                .flatMap { source ->
                    childBlock(lex.drop(2))
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
 * Parse `download` statement
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
 * Parse `concurrently` statement
 */
private fun concurrently(lex: List<HitLexeme>): ParseResult {
    return expectSymbol(lex.firstOrNull())
        .flatMap {
            val n: Int? = it.sym.toIntOrNull()

            if (n != null) {
                childBlock(lex.drop(1))
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
 * Parse a child block
 * @return parsed child block or empty statements list if missing
 */
private fun childBlock(script: List<HitLexeme>): ParseResult {
    val head = script.firstOrNull()

    return when (head) {
        is HitLexeme.Ident -> block(emptyList(), script.drop(1))
        else -> Success(Parsed(Statements(), script))
    }
}

/**
 * Parses a statements block until Dedent or EOF
 */
private tailrec fun block(script: List<HitSyntax>, lex: List<HitLexeme>): ParseResult {
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
 * Parse input lexemes list [lex] into [HitSyntax] AST
 */
fun parse(lex: List<HitLexeme>): Result<HitSyntax, ParseError> {
    return block(emptyList(), lex).map { it.script }
}
