package riichinexus.microservices.shared.api.docs

import OpenApiContractModel.*

private[docs] object PublicOpenApiPathSpecs:
  def paths: Vector[PathSpec] = Vector(
    PathSpec(
      "/public/tournaments",
      "get",
      OperationSpec(
        summary = "Public tournament index",
        description = "Returns the public tournament index page response.",
        tags = Vector("public"),
        responseRef = Some("PublicTournamentSummaryPage")
      )
    ),
    PathSpec(
      "/public/tournaments/{id}",
      "get",
      OperationSpec(
        summary = "Public tournament detail",
        description = "Returns the public tournament detail view including stages, standings and brackets.",
        tags = Vector("public"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id")
        ),
        responseRef = Some("PublicTournamentDetailView")
      )
    ),
    PathSpec(
      "/public/clubs/{clubId}",
      "get",
      OperationSpec(
        summary = "Public club detail",
        description = "Returns public club detail including recruitment policy, current lineup and recent matches.",
        tags = Vector("public"),
        parameters = Vector(
          ParameterSpec("clubId", "path", required = true, "Club id")
        ),
        responseRef = Some("PublicClubDetailView")
      )
    )
  )
