package hittassign.dsl

import com.github.kittinunf.result.Result

sealed class ParseError : Exception()

/**
 * Parse input lexemes list [lex] into AST
 */
fun parse(lex: List<HitLexeme>): Result<HitSyntax, ParseError> {
    // TODO implement me
    return Result.Success(
        Fetch(
            valKey("h").get(), StringTpl("https://hitta"), Statements(
                Foreach(
                    valKey("c").get(), ValPath(valKey("h").get(), "company.companies"), Statements(
                        Fetch(
                            ValKey.of("v").get(), StringTpl("https://foursquare/match/{c.long,c.lat}"), Statements(
                                Fetch(
                                    ValKey.of("photo").get(), StringTpl("https://foursquare/photos/{v.id}"), Statements(
                                        Foreach(
                                            valKey("p").get(), ValPath(valKey("photo").get(), "$"), Statements(
                                                Download(
                                                    ValPath(valKey("p").get(), "url"),
                                                    StringTpl("/localpath/venue{c.id}/{p.name}")
                                                ),
                                                Download(
                                                    ValPath(valKey("p").get(), "url"),
                                                    StringTpl("/localpath/venue1{c.id}/{p.name}")
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
