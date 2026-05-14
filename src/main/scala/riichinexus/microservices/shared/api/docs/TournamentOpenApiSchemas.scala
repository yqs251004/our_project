package riichinexus.microservices.shared.api.docs

import ujson.{Obj, Value}

import OpenApiContractModel.*

private[docs] object TournamentOpenApiSchemas:
  def schemas: Map[String, Value] = Map(
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
    "AppealTicket" -> objectSchema(
      "id" -> Obj("type" -> "string"),
      "tournamentId" -> Obj("type" -> "string"),
      "stageId" -> Obj("type" -> "string"),
      "tableId" -> Obj("type" -> "string"),
      "status" -> Obj("type" -> "string"),
      "priority" -> Obj("type" -> "string")
    ),
    "AppealTicketPage" -> pageSchema("#/components/schemas/AppealTicket")
  )
