package hittassign.dsl

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.map
import com.github.kittinunf.result.mapError
import com.jayway.jsonpath.JsonPath

sealed class ParseError : Exception()

fun isValidValKey(key: String): Boolean = Regex("""^[a-zA-Z_${'$'}][a-zA-Z0-9_${'$'}]*$""").matches(key)

/**
 * Invalid ValSpec value error
 */
object InvalidValPath : Exception()

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

/**
 * Parse input lexemes list [lex] into AST
 */
fun parse(lex: List<HitLexeme>): Result<HitSyntax, ParseError> {
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

fun parse_bak(lex: List<HitLexeme>): Result<HitSyntax, ParseError> {
    // TODO implement me
    val basePath = "/Users/vadim/projects/lostintime/hittassign-back/tmp/"

    return Result.Success(
        Statements(
            Debug(StringTpl(ConstStrPart("Hello there!!!! this is debug message"))),
            Fetch(
                ValBind("h"),
                StringTpl(ConstStrPart("https://api.hitta.se/search/v7/app/combined/within/57.840703831916%3A11.728156448084002%2C57.66073920808401%3A11.908121071915998/?range.to=101&range.from=1&geo.hint=57.75072152%3A11.81813876&sort.order=relevance&query=lunch")),
                Statements(
                    Foreach(
                        ValBind("c"), ValSpec(ValBind("h"), JsonPath.compile("$.company.companies")), Statements(
                            Fetch(
                                ValBind("v"), // fetch into v
                                StringTpl(
                                    ConstStrPart("https://foursquare/match/"),
                                    ValSpecPart(ValSpec(ValBind("c"), JsonPath.compile("$.long"))),
                                    ConstStrPart(","),
                                    ValSpecPart(ValSpec(ValBind("c"), JsonPath.compile("$.lat")))
                                ),
                                Statements(
                                    Fetch(
                                        ValBind("photo"),
                                        StringTpl(
                                            ConstStrPart("https://foursquare/photos/"),
                                            ValSpecPart(ValSpec(ValBind("v"), JsonPath.compile("$.id")))
                                        ),
                                        Statements(
                                            Foreach(
                                                ValBind("p"),
                                                ValSpec(ValBind("photo"), JsonPath.compile("$")),
                                                Statements(
                                                    Download(
                                                        ValSpec(ValBind("p"), JsonPath.compile("$.url")),
                                                        StringTpl(
                                                            ConstStrPart(basePath),
                                                            ValSpecPart(
                                                                ValSpec(
                                                                    ValBind("c"),
                                                                    JsonPath.compile("$.id")
                                                                )
                                                            ),
                                                            ConstStrPart("/"),
                                                            ValSpecPart(
                                                                ValSpec(
                                                                    ValBind("p"),
                                                                    JsonPath.compile("$.name")
                                                                )
                                                            )
                                                        )
                                                    ),
                                                    Download(
                                                        ValSpec(ValBind("p"), JsonPath.compile("$.url")),
                                                        StringTpl(
                                                            ConstStrPart(basePath),
                                                            ValSpecPart(
                                                                ValSpec(
                                                                    ValBind("c"),
                                                                    JsonPath.compile("$.id")
                                                                )
                                                            ),
                                                            ConstStrPart("/"),
                                                            ValSpecPart(
                                                                ValSpec(
                                                                    ValBind("p"),
                                                                    JsonPath.compile("$.name")
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
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
