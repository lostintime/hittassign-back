package hittassign.dsl

import com.github.kittinunf.result.Result

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
        Result.Success(ValSpec(ValBind(key), jsonPath))
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
        Fetch(
            ValBind("h"), StringTpl(ConstStrPart("https://hitta")), Statements(
                Foreach(
                    ValBind("c"), ValSpec(ValBind("h"), "company.companies"), Statements(
                        Fetch(
                            ValBind("v"), // fetch into v
                            StringTpl(
                                ConstStrPart("https://foursquare/match/"),
                                ValSpecPart(ValSpec(ValBind("c"), "long")),
                                ConstStrPart(","),
                                ValSpecPart(ValSpec(ValBind("c"), "lat"))
                            ),
                            Statements(
                                Fetch(
                                    ValBind("photo"),
                                    StringTpl(
                                        ConstStrPart("https://foursquare/photos/"),
                                        ValSpecPart(ValSpec(ValBind("v"), "id"))
                                    ),
                                    Statements(
                                        Foreach(
                                            ValBind("p"), ValSpec(ValBind("photo"), ""), Statements(
                                                Download(
                                                    ValSpec(ValBind("p"), "url"),
                                                    StringTpl(
                                                        ConstStrPart(basePath),
                                                        ValSpecPart(ValSpec(ValBind("c"), "id")),
                                                        ConstStrPart("/"),
                                                        ValSpecPart(ValSpec(ValBind("p"), "name"))
                                                    )
                                                ),
                                                Download(
                                                    ValSpec(ValBind("p"), "url"),
                                                    StringTpl(
                                                        ConstStrPart(basePath),
                                                        ValSpecPart(ValSpec(ValBind("c"), "id")),
                                                        ConstStrPart("/"),
                                                        ValSpecPart(ValSpec(ValBind("p"), "name"))
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
