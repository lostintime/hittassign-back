package hittassign.dsl

import com.github.kittinunf.result.Result

sealed class ParseError : Exception()

/**
 * Parse input lexemes list [lex] into AST
 */
fun parse(lex: List<HitLexeme>): Result<HitSyntax, ParseError> {
    // TODO implement me
    return Result.Success(
            Fetch(bindKey("h").get(), Const("https://hitta"), Statements(
                    Foreach(bindKey("c").get(), BindSpec(bindKey("h").get(), "company.companies"), Statements(
                            Fetch(BindKey.of("v").get(), Const("https://foursquare/match/{c.long,c.lat}"), Statements(
                                    Fetch(BindKey.of("photo").get(), Const("https://foursquare/photos/{v.id}"), Statements(
                                            Foreach(bindKey("p").get(), BindSpec(bindKey("photo").get(), "$"), Statements(
                                                    Download(BindSpec(bindKey("p").get(), "url"), Const("/localpath/venue{c.id}/{p.name}")),
                                                    Download(BindSpec(bindKey("p").get(), "url"), Const("/localpath/venue1{c.id}/{p.name}"))
                                            ))
                                    ))
                            ))
                    ))
            ))
    )
}
