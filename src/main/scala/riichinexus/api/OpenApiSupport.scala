package riichinexus.api

import ujson.{Arr, Obj, Str, Value}

object OpenApiSupport:
  private final case class ParameterSpec(
      name: String,
      in: String,
      required: Boolean,
      description: String,
      schemaType: String = "string"
  )

  private final case class OperationSpec(
      summary: String,
      description: String,
      tags: Vector[String],
      parameters: Vector[ParameterSpec] = Vector.empty,
      requestBody: Option[Value] = None,
      responseRef: Option[String] = None,
      responseDescription: String = "Successful response"
  )

  private final case class PathSpec(
      path: String,
      method: String,
      operation: OperationSpec
  )

  private def parameterJson(parameter: ParameterSpec): Value =
    Obj(
      "name" -> parameter.name,
      "in" -> parameter.in,
      "required" -> parameter.required,
      "description" -> parameter.description,
      "schema" -> Obj("type" -> parameter.schemaType)
    )

  private def requestBodyJson(body: Value): Value =
    Obj(
      "required" -> true,
      "content" -> Obj(
        "application/json" -> Obj(
          "schema" -> body
        )
      )
    )

  private def objectSchema(
      properties: (String, Value)*
  ): Value =
    Obj(
      "type" -> "object",
      "properties" -> Obj.from(properties),
      "additionalProperties" -> false
    )

  private val responseSchemaRefs = Map(
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

  private val frontendPaths = Vector(
    PathSpec(
      "/auth/register",
      "post",
      OperationSpec(
        summary = "Register account",
        description = "Registers a credential-backed account and creates or binds a player profile.",
        tags = Vector("auth"),
        requestBody = Some(
          objectSchema(
            "username" -> Obj("type" -> "string"),
            "password" -> Obj("type" -> "string"),
            "displayName" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("AuthSuccessView"),
        responseDescription = "Account registered"
      )
    ),
    PathSpec(
      "/auth/login",
      "post",
      OperationSpec(
        summary = "Login with account password",
        description = "Authenticates a credential-backed account and returns a bearer token.",
        tags = Vector("auth"),
        requestBody = Some(
          objectSchema(
            "username" -> Obj("type" -> "string"),
            "password" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("AuthSuccessView")
      )
    ),
    PathSpec(
      "/auth/session",
      "get",
      OperationSpec(
        summary = "Restore authenticated session",
        description = "Resolves the current credential-backed session from an Authorization bearer token.",
        tags = Vector("auth"),
        responseRef = Some("AuthSessionView")
      )
    ),
    PathSpec(
      "/auth/logout",
      "post",
      OperationSpec(
        summary = "Logout current session",
        description = "Invalidates the current credential-backed session token.",
        tags = Vector("auth")
      )
    ),
    PathSpec(
      "/session",
      "get",
      OperationSpec(
        summary = "Resolve current session",
        description = "Returns the current authenticated registered player, guest session, or anonymous session view.",
        tags = Vector("session"),
        parameters = Vector(
          ParameterSpec("operatorId", "query", required = false, "Registered player id"),
          ParameterSpec("guestSessionId", "query", required = false, "Guest session id")
        ),
        responseRef = Some("CurrentSessionView")
      )
    ),
    PathSpec(
      "/players/me",
      "get",
      OperationSpec(
        summary = "Resolve current player",
        description = "Returns the canonical Player aggregate for the current registered player.",
        tags = Vector("players"),
        parameters = Vector(
          ParameterSpec("operatorId", "query", required = true, "Current player id")
        ),
        responseRef = Some("Player")
      )
    ),
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
    ),
    PathSpec(
      "/tournaments/{id}",
      "get",
      OperationSpec(
        summary = "Tournament operations detail",
        description = "Returns the dedicated tournament detail view used by the operations workbench.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id")
        ),
        responseRef = Some("TournamentDetailView")
      )
    ),
    PathSpec(
      "/tournaments/{id}/publish",
      "post",
      OperationSpec(
        summary = "Publish tournament",
        description = "Publishes a tournament and returns the updated tournament mutation view.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id")
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
      "/tournaments/{id}/clubs/{clubId}",
      "post",
      OperationSpec(
        summary = "Register club in tournament",
        description = "Registers a club into a tournament and returns the updated tournament mutation view.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id"),
          ParameterSpec("clubId", "path", required = true, "Club id")
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
      "/tournaments/{id}/clubs/{clubId}/remove",
      "post",
      OperationSpec(
        summary = "Remove club from tournament",
        description = "Removes a club from tournament participation or invitation state.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id"),
          ParameterSpec("clubId", "path", required = true, "Club id")
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
      "/tournaments/{id}/stages",
      "get",
      OperationSpec(
        summary = "List tournament stages",
        description = "Returns the stable stage directory used by tournament operations pages.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id")
        ),
        responseRef = Some("TournamentStageDirectoryEntryList")
      )
    ),
    PathSpec(
      "/tournaments/{id}/stages/{stageId}/lineups",
      "post",
      OperationSpec(
        summary = "Submit stage lineup",
        description = "Submits or replaces a club lineup for a tournament stage and returns the updated tournament mutation view.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id"),
          ParameterSpec("stageId", "path", required = true, "Stage id")
        ),
        requestBody = Some(
          objectSchema(
            "clubId" -> Obj("type" -> "string"),
            "operatorId" -> Obj("type" -> "string"),
            "seats" -> Obj("type" -> "array", "items" -> Obj("type" -> "object")),
            "note" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("TournamentMutationView")
      )
    ),
    PathSpec(
      "/tournaments/{id}/stages/{stageId}/schedule",
      "post",
      OperationSpec(
        summary = "Schedule stage tables",
        description = "Schedules stage tables and returns the updated tournament mutation view together with scheduled tables.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id"),
          ParameterSpec("stageId", "path", required = true, "Stage id")
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
    ),
    PathSpec(
      "/tournaments/{id}/stages/{stageId}/standings",
      "get",
      OperationSpec(
        summary = "Stage standings",
        description = "Returns standings for a tournament stage.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id"),
          ParameterSpec("stageId", "path", required = true, "Stage id")
        ),
        responseRef = Some("StageRankingSnapshot")
      )
    ),
    PathSpec(
      "/tournaments/{id}/stages/{stageId}/bracket",
      "get",
      OperationSpec(
        summary = "Stage knockout bracket",
        description = "Returns the finals/knockout bracket snapshot.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id"),
          ParameterSpec("stageId", "path", required = true, "Stage id")
        ),
        responseRef = Some("KnockoutBracketSnapshot")
      )
    ),
    PathSpec(
      "/tables/{tableId}/seats/{seat}/state",
      "post",
      OperationSpec(
        summary = "Update seat readiness or disconnect state",
        description = "Updates a table seat's ready/disconnected state.",
        tags = Vector("tables"),
        parameters = Vector(
          ParameterSpec("tableId", "path", required = true, "Table id"),
          ParameterSpec("seat", "path", required = true, "Seat wind")
        ),
        requestBody = Some(
          objectSchema(
            "operatorId" -> Obj("type" -> "string"),
            "ready" -> Obj("type" -> "boolean"),
            "disconnected" -> Obj("type" -> "boolean"),
            "note" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("Table")
      )
    ),
    PathSpec(
      "/appeals",
      "get",
      OperationSpec(
        summary = "List appeals",
        description = "Returns the appeal work queue with triage filters.",
        tags = Vector("appeals"),
        responseRef = Some("AppealTicketPage")
      )
    ),
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

  private def pageSchema(itemRef: String): Value =
    objectSchema(
      "items" -> Obj("type" -> "array", "items" -> Obj("$ref" -> itemRef)),
      "total" -> Obj("type" -> "integer"),
      "limit" -> Obj("type" -> "integer"),
      "offset" -> Obj("type" -> "integer"),
      "hasMore" -> Obj("type" -> "boolean"),
      "appliedFilters" -> Obj("type" -> "object", "additionalProperties" -> Obj("type" -> "string"))
    )

  private def components: Value =
    Obj(
      "schemas" -> Obj(
        "AuthSuccessView" -> objectSchema(
          "userId" -> Obj("type" -> "string"),
          "username" -> Obj("type" -> "string"),
          "displayName" -> Obj("type" -> "string"),
          "token" -> Obj("type" -> "string"),
          "roles" -> Obj("type" -> "object")
        ),
        "AuthSessionView" -> objectSchema(
          "userId" -> Obj("type" -> "string"),
          "username" -> Obj("type" -> "string"),
          "displayName" -> Obj("type" -> "string"),
          "authenticated" -> Obj("type" -> "boolean"),
          "roles" -> Obj("type" -> "object")
        ),
        "CurrentSessionView" -> objectSchema(
          "principalKind" -> Obj("type" -> "string"),
          "principalId" -> Obj("type" -> "string"),
          "displayName" -> Obj("type" -> "string"),
          "authenticated" -> Obj("type" -> "boolean"),
          "roles" -> Obj("type" -> "object"),
          "player" -> Obj("type" -> "object"),
          "guestSession" -> Obj("type" -> "object")
        ),
        "Player" -> objectSchema(
          "id" -> Obj("type" -> "string"),
          "userId" -> Obj("type" -> "string"),
          "nickname" -> Obj("type" -> "string"),
          "registeredAt" -> Obj("type" -> "string", "format" -> "date-time"),
          "elo" -> Obj("type" -> "integer"),
          "status" -> Obj("type" -> "string"),
          "boundClubIds" -> Obj("type" -> "array", "items" -> Obj("type" -> "string")),
          "roleGrants" -> Obj("type" -> "array", "items" -> Obj("type" -> "object"))
        ),
        "ClubMembershipApplicantView" -> objectSchema(
          "playerId" -> Obj("type" -> "string"),
          "applicantUserId" -> Obj("type" -> "string"),
          "displayName" -> Obj("type" -> "string"),
          "playerStatus" -> Obj("type" -> "string"),
          "currentRank" -> Obj("type" -> "object"),
          "elo" -> Obj("type" -> "integer"),
          "clubIds" -> Obj("type" -> "array", "items" -> Obj("type" -> "string"))
        ),
        "ClubMembershipApplicationView" -> objectSchema(
          "applicationId" -> Obj("type" -> "string"),
          "clubId" -> Obj("type" -> "string"),
          "clubName" -> Obj("type" -> "string"),
          "applicant" -> Obj("$ref" -> "#/components/schemas/ClubMembershipApplicantView"),
          "submittedAt" -> Obj("type" -> "string", "format" -> "date-time"),
          "message" -> Obj("type" -> "string"),
          "status" -> Obj("type" -> "string"),
          "reviewedBy" -> Obj("type" -> "string"),
          "reviewedByDisplayName" -> Obj("type" -> "string"),
          "reviewedAt" -> Obj("type" -> "string", "format" -> "date-time"),
          "reviewNote" -> Obj("type" -> "string"),
          "withdrawnByPrincipalId" -> Obj("type" -> "string"),
          "canReview" -> Obj("type" -> "boolean"),
          "canWithdraw" -> Obj("type" -> "boolean")
        ),
        "ClubTournamentParticipationView" -> objectSchema(
          "clubId" -> Obj("type" -> "string"),
          "tournamentId" -> Obj("type" -> "string"),
          "name" -> Obj("type" -> "string"),
          "status" -> Obj("type" -> "string"),
          "clubParticipationStatus" -> Obj("type" -> "string"),
          "stageName" -> Obj("type" -> "string"),
          "startsAt" -> Obj("type" -> "string", "format" -> "date-time"),
          "endsAt" -> Obj("type" -> "string", "format" -> "date-time"),
          "canViewDetail" -> Obj("type" -> "boolean"),
          "canSubmitLineup" -> Obj("type" -> "boolean"),
          "canDecline" -> Obj("type" -> "boolean")
        ),
        "TournamentStageDirectoryEntry" -> objectSchema(
          "stageId" -> Obj("type" -> "string"),
          "name" -> Obj("type" -> "string"),
          "format" -> Obj("type" -> "string"),
          "order" -> Obj("type" -> "integer"),
          "status" -> Obj("type" -> "string"),
          "currentRound" -> Obj("type" -> "integer"),
          "roundCount" -> Obj("type" -> "integer"),
          "schedulingPoolSize" -> Obj("type" -> "integer"),
          "pendingTablePlanCount" -> Obj("type" -> "integer"),
          "scheduledTableCount" -> Obj("type" -> "integer")
        ),
        "TournamentParticipantClubView" -> objectSchema(
          "clubId" -> Obj("type" -> "string"),
          "clubName" -> Obj("type" -> "string"),
          "memberCount" -> Obj("type" -> "integer"),
          "activeMemberCount" -> Obj("type" -> "integer")
        ),
        "TournamentParticipantPlayerView" -> objectSchema(
          "playerId" -> Obj("type" -> "string"),
          "nickname" -> Obj("type" -> "string"),
          "status" -> Obj("type" -> "string"),
          "elo" -> Obj("type" -> "integer"),
          "currentRank" -> Obj("type" -> "object"),
          "clubIds" -> Obj("type" -> "array", "items" -> Obj("type" -> "string"))
        ),
        "TournamentWhitelistSummaryView" -> objectSchema(
          "totalEntries" -> Obj("type" -> "integer"),
          "clubCount" -> Obj("type" -> "integer"),
          "playerCount" -> Obj("type" -> "integer"),
          "clubIds" -> Obj("type" -> "array", "items" -> Obj("type" -> "string")),
          "playerIds" -> Obj("type" -> "array", "items" -> Obj("type" -> "string"))
        ),
        "TournamentLineupSubmissionView" -> objectSchema(
          "submissionId" -> Obj("type" -> "string"),
          "clubId" -> Obj("type" -> "string"),
          "clubName" -> Obj("type" -> "string"),
          "submittedBy" -> Obj("type" -> "string"),
          "submittedByDisplayName" -> Obj("type" -> "string"),
          "submittedAt" -> Obj("type" -> "string", "format" -> "date-time"),
          "activePlayerIds" -> Obj("type" -> "array", "items" -> Obj("type" -> "string")),
          "reservePlayerIds" -> Obj("type" -> "array", "items" -> Obj("type" -> "string")),
          "note" -> Obj("type" -> "string")
        ),
        "TournamentOperationsStageView" -> objectSchema(
          "stageId" -> Obj("type" -> "string"),
          "name" -> Obj("type" -> "string"),
          "format" -> Obj("type" -> "string"),
          "order" -> Obj("type" -> "integer"),
          "status" -> Obj("type" -> "string"),
          "currentRound" -> Obj("type" -> "integer"),
          "roundCount" -> Obj("type" -> "integer"),
          "schedulingPoolSize" -> Obj("type" -> "integer"),
          "pendingTablePlanCount" -> Obj("type" -> "integer"),
          "scheduledTableCount" -> Obj("type" -> "integer"),
          "lineupSubmissions" -> Obj(
            "type" -> "array",
            "items" -> Obj("$ref" -> "#/components/schemas/TournamentLineupSubmissionView")
          )
        ),
        "TournamentDetailView" -> objectSchema(
          "tournamentId" -> Obj("type" -> "string"),
          "name" -> Obj("type" -> "string"),
          "organizer" -> Obj("type" -> "string"),
          "status" -> Obj("type" -> "string"),
          "startsAt" -> Obj("type" -> "string", "format" -> "date-time"),
          "endsAt" -> Obj("type" -> "string", "format" -> "date-time"),
          "participatingClubs" -> Obj(
            "type" -> "array",
            "items" -> Obj("$ref" -> "#/components/schemas/TournamentParticipantClubView")
          ),
          "participatingPlayers" -> Obj(
            "type" -> "array",
            "items" -> Obj("$ref" -> "#/components/schemas/TournamentParticipantPlayerView")
          ),
          "whitelistSummary" -> Obj("$ref" -> "#/components/schemas/TournamentWhitelistSummaryView"),
          "stages" -> Obj(
            "type" -> "array",
            "items" -> Obj("$ref" -> "#/components/schemas/TournamentOperationsStageView")
          )
        ),
        "TournamentMutationView" -> objectSchema(
          "tournament" -> Obj("$ref" -> "#/components/schemas/TournamentDetailView"),
          "scheduledTables" -> Obj(
            "type" -> "array",
            "items" -> Obj("$ref" -> "#/components/schemas/Table")
          )
        ),
        "PublicTournamentDetailView" -> objectSchema(
          "tournamentId" -> Obj("type" -> "string"),
          "name" -> Obj("type" -> "string"),
          "organizer" -> Obj("type" -> "string"),
          "status" -> Obj("type" -> "string"),
          "startsAt" -> Obj("type" -> "string", "format" -> "date-time"),
          "endsAt" -> Obj("type" -> "string", "format" -> "date-time"),
          "clubIds" -> Obj("type" -> "array", "items" -> Obj("type" -> "string")),
          "playerIds" -> Obj("type" -> "array", "items" -> Obj("type" -> "string")),
          "whitelistCount" -> Obj("type" -> "integer"),
          "stages" -> Obj("type" -> "array", "items" -> Obj("type" -> "object"))
        ),
        "PublicClubDetailView" -> objectSchema(
          "clubId" -> Obj("type" -> "string"),
          "name" -> Obj("type" -> "string"),
          "memberCount" -> Obj("type" -> "integer"),
          "activeMemberCount" -> Obj("type" -> "integer"),
          "adminCount" -> Obj("type" -> "integer"),
          "powerRating" -> Obj("type" -> "number"),
          "totalPoints" -> Obj("type" -> "integer"),
          "treasuryBalance" -> Obj("type" -> "integer"),
          "pointPool" -> Obj("type" -> "integer"),
          "applicationPolicy" -> Obj("type" -> "object"),
          "currentLineup" -> Obj("type" -> "array", "items" -> Obj("type" -> "object")),
          "recentMatches" -> Obj("type" -> "array", "items" -> Obj("type" -> "object"))
        ),
        "StageRankingSnapshot" -> objectSchema(
          "tournamentId" -> Obj("type" -> "string"),
          "stageId" -> Obj("type" -> "string"),
          "generatedAt" -> Obj("type" -> "string", "format" -> "date-time"),
          "entries" -> Obj("type" -> "array", "items" -> Obj("type" -> "object"))
        ),
        "KnockoutBracketSnapshot" -> objectSchema(
          "tournamentId" -> Obj("type" -> "string"),
          "stageId" -> Obj("type" -> "string"),
          "generatedAt" -> Obj("type" -> "string", "format" -> "date-time"),
          "rounds" -> Obj("type" -> "array", "items" -> Obj("type" -> "object"))
        ),
        "AdvancedStatsBoard" -> objectSchema(
          "owner" -> Obj("type" -> "object"),
          "sampleSize" -> Obj("type" -> "integer"),
          "defenseStability" -> Obj("type" -> "number"),
          "ukeireExpectation" -> Obj("type" -> "number"),
          "lastUpdatedAt" -> Obj("type" -> "string", "format" -> "date-time")
        ),
        "Club" -> objectSchema(
          "id" -> Obj("type" -> "string"),
          "name" -> Obj("type" -> "string"),
          "members" -> Obj("type" -> "array", "items" -> Obj("type" -> "string")),
          "admins" -> Obj("type" -> "array", "items" -> Obj("type" -> "string")),
          "recruitmentPolicy" -> Obj("type" -> "object")
        ),
        "Table" -> objectSchema(
          "id" -> Obj("type" -> "string"),
          "tableNo" -> Obj("type" -> "integer"),
          "status" -> Obj("type" -> "string"),
          "seats" -> Obj("type" -> "array", "items" -> Obj("type" -> "object"))
        ),
        "PublicTournamentSummaryView" -> objectSchema(
          "tournamentId" -> Obj("type" -> "string"),
          "name" -> Obj("type" -> "string"),
          "organizer" -> Obj("type" -> "string"),
          "status" -> Obj("type" -> "string"),
          "startsAt" -> Obj("type" -> "string", "format" -> "date-time"),
          "endsAt" -> Obj("type" -> "string", "format" -> "date-time"),
          "stageCount" -> Obj("type" -> "integer"),
          "activeStageCount" -> Obj("type" -> "integer"),
          "participantCount" -> Obj("type" -> "integer")
        ),
        "AppealTicket" -> objectSchema(
          "id" -> Obj("type" -> "string"),
          "tournamentId" -> Obj("type" -> "string"),
          "stageId" -> Obj("type" -> "string"),
          "tableId" -> Obj("type" -> "string"),
          "status" -> Obj("type" -> "string"),
          "priority" -> Obj("type" -> "string")
        ),
        "ClubMembershipApplicationPage" -> pageSchema("#/components/schemas/ClubMembershipApplicationView"),
        "ClubTournamentParticipationPage" -> pageSchema("#/components/schemas/ClubTournamentParticipationView"),
        "ClubPage" -> pageSchema("#/components/schemas/Club"),
        "PublicTournamentSummaryPage" -> pageSchema("#/components/schemas/PublicTournamentSummaryView"),
        "AppealTicketPage" -> pageSchema("#/components/schemas/AppealTicket")
      )
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
