package riichinexus.microservices.tournament.objects.recordmanagement.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class TournamentMatchRecordSeatResultView(
    playerId: String,
    seat: String,
    clubId: Option[String],
    finalPoints: Int,
    placement: Int,
    scoreDelta: Int,
    uma: Double,
    oka: Double
) derives CanEqual

object TournamentMatchRecordSeatResultView:
  def fromDomain(result: MatchRecordSeatResult): TournamentMatchRecordSeatResultView =
    TournamentMatchRecordSeatResultView(
      playerId = result.playerId.value,
      seat = result.seat.toString,
      clubId = result.clubId.map(_.value),
      finalPoints = result.finalPoints,
      placement = result.placement,
      scoreDelta = result.scoreDelta,
      uma = result.uma,
      oka = result.oka
    )

  given ReadWriter[TournamentMatchRecordSeatResultView] = macroRW

