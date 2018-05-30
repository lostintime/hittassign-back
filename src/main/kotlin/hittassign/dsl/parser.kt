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


sealed class BlockParser {
    data class Empty(val depth: Int) : BlockParser()
}

/**
 * Language parser state machine
 */
sealed class HitParser {
    abstract fun parse(l: HitLexeme): HitParser

    /**
     * Initial parser state: got only empty lines or not statements
     */
    object Empty : HitParser() {
        override fun parse(l: HitLexeme): HitParser {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }

    /**
     * Successfully parsed
     */
    data class Success(val script: HitSyntax) : HitParser() {
        override fun parse(l: HitLexeme): HitParser {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }
}

/**
 * Parse input lexemes list [lex] into AST
 */
fun parse(lex: List<HitLexeme>): Result<HitSyntax, ParseError> {


    return demo()
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


