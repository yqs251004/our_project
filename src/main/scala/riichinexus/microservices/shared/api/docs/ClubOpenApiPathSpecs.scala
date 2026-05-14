package riichinexus.microservices.shared.api.docs

import ujson.{Arr, Obj}

import OpenApiContractModel.*

private[docs] object ClubOpenApiPathSpecs:
  def paths: Vector[PathSpec] = Vector(
    PathSpec(
      "/clubs/{clubId}/applications",
      "get",
      OperationSpec(
        summary = "List club applications",
        description = "Returns the club admin application inbox for a club.",
        tags = Vector("clubs"),
        parameters = Vector(
          ParameterSpec("clubId", "path", required = true, "Club id"),
          ParameterSpec("operatorId", "query", required = true, "Club admin operator id"),
          ParameterSpec("status", "query", required = false, "Application status"),
          ParameterSpec("limit", "query", required = false, "Page size", "integer"),
          ParameterSpec("offset", "query", required = false, "Page offset", "integer")
        ),
        responseRef = Some("ClubMembershipApplicationPage")
      )
    ),
    PathSpec(
      "/clubs/{clubId}/applications/{membershipId}",
      "get",
      OperationSpec(
        summary = "Get club application detail",
        description = "Returns a single club application detail for admins or the owning applicant.",
        tags = Vector("clubs"),
        parameters = Vector(
          ParameterSpec("clubId", "path", required = true, "Club id"),
          ParameterSpec("membershipId", "path", required = true, "Membership application id"),
          ParameterSpec("operatorId", "query", required = false, "Registered player id"),
          ParameterSpec("guestSessionId", "query", required = false, "Guest session id")
        ),
        responseRef = Some("ClubMembershipApplicationView")
      )
    ),
    PathSpec(
      "/clubs/{clubId}/applications/current",
      "get",
      OperationSpec(
        summary = "Get current applicant club application",
        description = "Returns the caller's current pending club application for the club.",
        tags = Vector("clubs"),
        parameters = Vector(
          ParameterSpec("clubId", "path", required = true, "Club id"),
          ParameterSpec("operatorId", "query", required = false, "Registered player id"),
          ParameterSpec("guestSessionId", "query", required = false, "Guest session id")
        ),
        responseRef = Some("ClubMembershipApplicationView")
      )
    ),
    PathSpec(
      "/clubs/{clubId}/applications/{membershipId}/review",
      "post",
      OperationSpec(
        summary = "Review club application",
        description = "Approve or reject a club application. Supports guest-origin approvals via optional playerId binding.",
        tags = Vector("clubs"),
        parameters = Vector(
          ParameterSpec("clubId", "path", required = true, "Club id"),
          ParameterSpec("membershipId", "path", required = true, "Membership application id")
        ),
        requestBody = Some(
          objectSchema(
            "operatorId" -> Obj("type" -> "string"),
            "decision" -> Obj("type" -> "string", "enum" -> Arr("approve", "reject")),
            "playerId" -> Obj("type" -> "string"),
            "note" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("ClubMembershipApplicationView")
      )
    ),
    PathSpec(
      "/clubs/{clubId}/tournaments",
      "get",
      OperationSpec(
        summary = "List club tournaments",
        description = "Returns the club-facing tournament participation and invitation list. scope=recent includes active tournaments and tournaments that ended within the last 90 days.",
        tags = Vector("clubs"),
        parameters = Vector(
          ParameterSpec("clubId", "path", required = true, "Club id"),
          ParameterSpec("scope", "query", required = false, "One of recent, active, all. recent includes active tournaments and tournaments that ended within the last 90 days."),
          ParameterSpec("viewer", "query", required = false, "Operator id for role-sensitive flags"),
          ParameterSpec("limit", "query", required = false, "Page size", "integer"),
          ParameterSpec("offset", "query", required = false, "Page offset", "integer")
        ),
        responseRef = Some("ClubTournamentParticipationPage")
      )
    ),
    PathSpec(
      "/clubs/{clubId}/tournaments/{tournamentId}/accept",
      "post",
      OperationSpec(
        summary = "Accept club tournament participation",
        description = "Accepts a club invitation or confirms club participation in a tournament.",
        tags = Vector("clubs", "tournaments"),
        parameters = Vector(
          ParameterSpec("clubId", "path", required = true, "Club id"),
          ParameterSpec("tournamentId", "path", required = true, "Tournament id")
        ),
        requestBody = Some(
          objectSchema(
            "operatorId" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("TournamentMutationView")
      )
    ),
    PathSpec(
      "/clubs/{clubId}/tournaments/{tournamentId}/decline",
      "post",
      OperationSpec(
        summary = "Decline or withdraw club tournament participation",
        description = "Declines an invitation or withdraws the club from tournament participation.",
        tags = Vector("clubs", "tournaments"),
        parameters = Vector(
          ParameterSpec("clubId", "path", required = true, "Club id"),
          ParameterSpec("tournamentId", "path", required = true, "Tournament id")
        ),
        requestBody = Some(
          objectSchema(
            "operatorId" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("TournamentMutationView")
      )
    ),
    PathSpec(
      "/clubs",
      "get",
      OperationSpec(
        summary = "List clubs",
        description = "Lists clubs with activeOnly and joinableOnly filtering.",
        tags = Vector("clubs"),
        parameters = Vector(
          ParameterSpec("activeOnly", "query", required = false, "Only active clubs", "boolean"),
          ParameterSpec("joinableOnly", "query", required = false, "Only clubs with applicationsOpen=true", "boolean")
        ),
        responseRef = Some("ClubPage")
      )
    ),
    PathSpec(
      "/clubs/{clubId}/recruitment-policy",
      "post",
      OperationSpec(
        summary = "Update recruitment policy",
        description = "Updates the backend-defined recruitment policy that drives joinableOnly and public application metadata.",
        tags = Vector("clubs"),
        parameters = Vector(
          ParameterSpec("clubId", "path", required = true, "Club id")
        ),
        requestBody = Some(
          objectSchema(
            "operatorId" -> Obj("type" -> "string"),
            "applicationsOpen" -> Obj("type" -> "boolean"),
            "requirementsText" -> Obj("type" -> "string"),
            "expectedReviewSlaHours" -> Obj("type" -> "integer"),
            "note" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("Club")
      )
    )
  )
