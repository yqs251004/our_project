package riichinexus.microservices.tournament.objects.paifumanagement.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.paifumanagement.FinalStanding
import upickle.default.*

final case class PaifuSummary(
    paifuId: String,
    tableId: String,
    tournamentId: String,
    stageId: String,
    recordedAt: String,
    source: String,
    matchRecordId: Option[String],
    totalHands: Int,
    playerIds: Vector[String],
    finalStandings: Vector[FinalStanding],
    roundScoreChanges: Vector[PaifuRoundScoreChanges]
) derives CanEqual, ReadWriter
