package riichinexus.system.api.docs

import ujson.{Arr, Obj, Str, Value}

import OpenApiContractModel.*

object OpenApiSupport:
  private def responseSchemaRefs: Map[String, Value] = Map(
    "AuthSuccessView" -> Obj("$ref" -> "#/components/schemas/AuthSuccessView"),
    "AuthSessionView" -> Obj("$ref" -> "#/components/schemas/AuthSessionView"),
    "CurrentSessionView" -> Obj("$ref" -> "#/components/schemas/CurrentSessionView"),
    "Player" -> Obj("$ref" -> "#/components/schemas/Player"),
    "ClubMembershipApplicationView" -> Obj("$ref" -> "#/components/schemas/ClubMembershipApplicationView"),
    "ClubMembershipApplicationPage" -> Obj("$ref" -> "#/components/schemas/ClubMembershipApplicationPage"),
    "ClubTournamentParticipationPage" -> Obj("$ref" -> "#/components/schemas/ClubTournamentParticipationPage"),
    "Club" -> Obj("$ref" -> "#/components/schemas/Club"),
    "ClubPage" -> Obj("$ref" -> "#/components/schemas/ClubPage"),
    "TournamentDetailView" -> Obj("$ref" -> "#/components/schemas/TournamentDetailView"),
    "TournamentMutationView" -> Obj("$ref" -> "#/components/schemas/TournamentMutationView"),
    "TournamentStageDirectoryEntryList" -> Obj(
      "type" -> "array",
      "items" -> Obj("$ref" -> "#/components/schemas/TournamentStageDirectoryEntry")
    ),
    "PublicTournamentSummaryPage" -> Obj(
      "$ref" -> "#/components/schemas/PublicTournamentSummaryPage"
    ),
    "PublicTournamentDetailView" -> Obj("$ref" -> "#/components/schemas/PublicTournamentDetailView"),
    "PublicClubDetailView" -> Obj("$ref" -> "#/components/schemas/PublicClubDetailView"),
    "StageRankingSnapshot" -> Obj("$ref" -> "#/components/schemas/StageRankingSnapshot"),
    "KnockoutBracketSnapshot" -> Obj("$ref" -> "#/components/schemas/KnockoutBracketSnapshot"),
    "AppealTicketPage" -> Obj("$ref" -> "#/components/schemas/AppealTicketPage"),
    "AdvancedStatsBoard" -> Obj("$ref" -> "#/components/schemas/AdvancedStatsBoard"),
    "Table" -> Obj("$ref" -> "#/components/schemas/Table")
  )

  private def frontendPaths: Vector[PathSpec] = OpenApiPathSpecs.frontendPaths

  private def componentSchemas: Map[String, Value] =
    SharedOpenApiSchemas.schemas ++
      AuthOpenApiSchemas.schemas ++
      ClubOpenApiSchemas.schemas ++
      PublicOpenApiSchemas.schemas ++
      DictionaryOpenApiSchemas.schemas ++
      AnalyticsOpenApiSchemas.schemas

  private def components: Value =
    Obj(
      "schemas" -> Obj.from(componentSchemas)
    )

  def openApiJson(baseUrl: String = "http://localhost:8080"): String =
    val paths = frontendPaths.groupBy(_.path).foldLeft(Obj()) { case (acc, (path, specs)) =>
      val methods = specs.foldLeft(Obj()) { case (methodAcc, spec) =>
        val responseSchema = spec.operation.responseRef.flatMap(responseSchemaRefs.get).getOrElse(Obj("type" -> "object"))
        methodAcc(spec.method) = Obj(
          "summary" -> spec.operation.summary,
          "description" -> spec.operation.description,
          "tags" -> Arr.from(spec.operation.tags.map(Str(_))),
          "parameters" -> Arr.from(spec.operation.parameters.map(parameterJson)),
          "responses" -> Obj(
            "200" -> Obj(
              "description" -> spec.operation.responseDescription,
              "content" -> Obj(
                "application/json" -> Obj(
                  "schema" -> responseSchema
                )
              )
            )
          )
        )
        spec.operation.requestBody.foreach(body => methodAcc(spec.method)("requestBody") = requestBodyJson(body))
        methodAcc
      }
      acc(path) = methods
      acc
    }

    ujson.write(
      Obj(
        "openapi" -> "3.1.0",
        "info" -> Obj(
          "title" -> "RiichiNexus Frontend Contract API",
          "version" -> "0.1.0",
          "description" -> "Generated OpenAPI contract for the frontend-facing RiichiNexus endpoints."
        ),
        "servers" -> Arr(Obj("url" -> baseUrl)),
        "tags" -> Arr(
          Obj("name" -> "auth"),
          Obj("name" -> "session"),
          Obj("name" -> "players"),
          Obj("name" -> "clubs"),
          Obj("name" -> "tournaments"),
          Obj("name" -> "public"),
          Obj("name" -> "tables"),
          Obj("name" -> "appeals"),
          Obj("name" -> "analytics")
        ),
        "paths" -> paths,
        "components" -> components
      ),
      indent = 2
    )

  def swaggerHtml(openApiPath: String = "/openapi.json"): String =
    s"""<!DOCTYPE html>
       |<html lang="en">
       |  <head>
       |    <meta charset="utf-8" />
       |    <title>RiichiNexus Swagger</title>
       |    <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css" />
       |  </head>
       |  <body>
       |    <div id="swagger-ui"></div>
       |    <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
       |    <script>
       |      window.onload = () => {
       |        window.ui = SwaggerUIBundle({
       |          url: '$openApiPath',
       |          dom_id: '#swagger-ui'
       |        });
       |      };
       |    </script>
       |  </body>
       |</html>
       |""".stripMargin
