package riichinexus.microservices.shared.api.docs

import OpenApiContractModel.*

private[docs] object AnalyticsOpenApiPathSpecs:
  def paths: Vector[PathSpec] = Vector(
    PathSpec(
      "/advanced-stats/players/{playerId}",
      "get",
      OperationSpec(
        summary = "Player advanced stats",
        description = "Returns the advanced stats board for a player.",
        tags = Vector("analytics"),
        parameters = Vector(
          ParameterSpec("playerId", "path", required = true, "Player id"),
          ParameterSpec("operatorId", "query", required = true, "Operator id")
        ),
        responseRef = Some("AdvancedStatsBoard")
      )
    ),
    PathSpec(
      "/advanced-stats/clubs/{clubId}",
      "get",
      OperationSpec(
        summary = "Club advanced stats",
        description = "Returns the advanced stats board for a club.",
        tags = Vector("analytics"),
        parameters = Vector(
          ParameterSpec("clubId", "path", required = true, "Club id"),
          ParameterSpec("operatorId", "query", required = true, "Operator id")
        ),
        responseRef = Some("AdvancedStatsBoard")
      )
    )
  )
