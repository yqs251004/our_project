package riichinexus.microservices.tournament.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*

final case class TournamentPaifuFinalStandingView(
    playerId: PlayerId,
    seat: SeatWind,
    finalPoints: Int,
    placement: Int,
    uma: Double,
    oka: Double
) derives CanEqual

object TournamentPaifuFinalStandingView:
  def fromDomain(standing: FinalStanding): TournamentPaifuFinalStandingView =
    TournamentPaifuFinalStandingView(
      playerId = standing.playerId,
      seat = standing.seat,
      finalPoints = standing.finalPoints,
      placement = standing.placement,
      uma = standing.uma,
      oka = standing.oka
    )

final case class TournamentPaifuSummaryView(
    paifuId: PaifuId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    recordedAt: Instant,
    source: String,
    matchRecordId: Option[MatchRecordId],
    totalHands: Int,
    playerIds: Vector[PlayerId],
    finalStandings: Vector[TournamentPaifuFinalStandingView]
) derives CanEqual

object TournamentPaifuSummaryView:
  def fromDomain(paifu: Paifu): TournamentPaifuSummaryView =
    TournamentPaifuSummaryView(
      paifuId = paifu.id,
      tableId = paifu.metadata.tableId,
      tournamentId = paifu.metadata.tournamentId,
      stageId = paifu.metadata.stageId,
      recordedAt = paifu.metadata.recordedAt,
      source = paifu.metadata.source,
      matchRecordId = paifu.metadata.matchRecordId,
      totalHands = paifu.totalHands,
      playerIds = paifu.playerIds,
      finalStandings = paifu.finalStandings.map(TournamentPaifuFinalStandingView.fromDomain)
    )
