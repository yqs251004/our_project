package riichinexus.system.api.docs

import ujson.{Obj, Value}

import riichinexus.system.api.docs.{OperationSpec, ParameterSpec, PathSpec}
import riichinexus.system.api.docs.OpenApiContractModel.*

object PublicOpenApiSchemas:
  def schemas: Map[String, Value] = Map(
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
    "PublicTournamentSummaryPage" -> pageSchema("#/components/schemas/PublicTournamentSummaryView")
  )
