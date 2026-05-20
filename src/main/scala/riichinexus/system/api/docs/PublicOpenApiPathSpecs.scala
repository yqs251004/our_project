package riichinexus.system.api.docs

import riichinexus.system.api.docs.{OperationSpec, ParameterSpec, PathSpec}
import riichinexus.system.api.docs.OpenApiContractModel.*

object PublicOpenApiPathSpecs:
  def paths: Vector[PathSpec] = Vector(
    PathSpec(
      "/api/listpublictournamentsapi",
      "post",
      OperationSpec(
        summary = "Public tournament index",
        description = "Returns the public tournament index page response from ListPublicTournamentsAPI.",
        tags = Vector("public"),
        responseRef = Some("PublicTournamentSummaryPage")
      )
    ),
    PathSpec(
      "/api/getpublictournamentapi",
      "post",
      OperationSpec(
        summary = "Public tournament detail",
        description = "Returns the public tournament detail view including stages, standings and brackets.",
        tags = Vector("public"),
        responseRef = Some("PublicTournamentDetailView")
      )
    ),
    PathSpec(
      "/api/getpublicclubapi",
      "post",
      OperationSpec(
        summary = "Public club detail",
        description = "Returns public club detail including recruitment policy, current lineup and recent matches.",
        tags = Vector("public"),
        responseRef = Some("PublicClubDetailView")
      )
    )
  )
