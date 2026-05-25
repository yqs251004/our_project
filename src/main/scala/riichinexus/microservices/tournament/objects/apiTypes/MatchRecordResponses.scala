package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*

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

final case class TournamentMatchRecordView(
    recordId: String,
    tableId: String,
    tournamentId: String,
    stageId: String,
    stageRoundNumber: Int,
    generatedAt: String,
    seatResults: Vector[TournamentMatchRecordSeatResultView],
    paifuId: Option[String],
    finalizedBy: Option[String],
    sourceEvent: String,
    notes: Vector[String]
) derives CanEqual

object TournamentMatchRecordView:
  def fromDomain(record: MatchRecord): TournamentMatchRecordView =
    TournamentMatchRecordView(
      recordId = record.id.value,
      tableId = record.tableId.value,
      tournamentId = record.tournamentId.value,
      stageId = record.stageId.value,
      stageRoundNumber = record.stageRoundNumber,
      generatedAt = record.generatedAt.toString,
      seatResults = record.seatResults.map(TournamentMatchRecordSeatResultView.fromDomain),
      paifuId = record.paifuId.map(_.value),
      finalizedBy = record.finalizedBy.map(_.value),
      sourceEvent = record.sourceEvent,
      notes = record.notes
    )
