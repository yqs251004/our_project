package riichinexus.microservices.shared.api.docs

import ujson.{Obj, Value}

import OpenApiContractModel.*

private[docs] object ClubOpenApiSchemas:
  def schemas: Map[String, Value] = Map(
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
    "Club" -> objectSchema(
      "id" -> Obj("type" -> "string"),
      "name" -> Obj("type" -> "string"),
      "members" -> Obj("type" -> "array", "items" -> Obj("type" -> "string")),
      "admins" -> Obj("type" -> "array", "items" -> Obj("type" -> "string")),
      "recruitmentPolicy" -> Obj("type" -> "object")
    ),
    "ClubMembershipApplicationPage" -> pageSchema("#/components/schemas/ClubMembershipApplicationView"),
    "ClubTournamentParticipationPage" -> pageSchema("#/components/schemas/ClubTournamentParticipationView"),
    "ClubPage" -> pageSchema("#/components/schemas/Club")
  )
