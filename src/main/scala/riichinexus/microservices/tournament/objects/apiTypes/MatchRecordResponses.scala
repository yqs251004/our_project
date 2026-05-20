package riichinexus.microservices.tournament.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*

final case class TournamentMatchRecordSeatResultView(
    playerId: PlayerId,
    seat: SeatWind,
    clubId: Option[ClubId],
    finalPoints: Int,
    placement: Int,
    scoreDelta: Int,
    uma: Double,
    oka: Double
) derives CanEqual

object TournamentMatchRecordSeatResultView:
  def fromDomain(result: MatchRecordSeatResult): TournamentMatchRecordSeatResultView =
    TournamentMatchRecordSeatResultView(
      playerId = result.playerId,
      seat = result.seat,
      clubId = result.clubId,
      finalPoints = result.finalPoints,
      placement = result.placement,
      scoreDelta = result.scoreDelta,
      uma = result.uma,
      oka = result.oka
    )

final case class TournamentMatchRecordView(
    recordId: MatchRecordId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    stageRoundNumber: Int,
    generatedAt: Instant,
    seatResults: Vector[TournamentMatchRecordSeatResultView],
    paifuId: Option[PaifuId],
    finalizedBy: Option[PlayerId],
    sourceEvent: String,
    notes: Vector[String]
) derives CanEqual

object TournamentMatchRecordView:
  def fromDomain(record: MatchRecord): TournamentMatchRecordView =
    TournamentMatchRecordView(
      recordId = record.id,
      tableId = record.tableId,
      tournamentId = record.tournamentId,
      stageId = record.stageId,
      stageRoundNumber = record.stageRoundNumber,
      generatedAt = record.generatedAt,
      seatResults = record.seatResults.map(TournamentMatchRecordSeatResultView.fromDomain),
      paifuId = record.paifuId,
      finalizedBy = record.finalizedBy,
      sourceEvent = record.sourceEvent,
      notes = record.notes
    )
