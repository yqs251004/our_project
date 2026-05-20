package riichinexus.microservices.tournament.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*

final case class TournamentTableSeatView(
    seat: SeatWind,
    playerId: PlayerId,
    initialPoints: Int,
    disconnected: Boolean,
    ready: Boolean,
    clubId: Option[ClubId]
) derives CanEqual

object TournamentTableSeatView:
  def fromDomain(seat: TableSeat): TournamentTableSeatView =
    TournamentTableSeatView(
      seat = seat.seat,
      playerId = seat.playerId,
      initialPoints = seat.initialPoints,
      disconnected = seat.disconnected,
      ready = seat.ready,
      clubId = seat.clubId
    )

final case class TournamentTableView(
    tableId: TableId,
    tableNo: Int,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    seats: Vector[TournamentTableSeatView],
    stageRoundNumber: Int,
    bracketMatchId: Option[String],
    bracketRoundNumber: Option[Int],
    status: TableStatus,
    startedAt: Option[Instant],
    scoringStartedAt: Option[Instant],
    endedAt: Option[Instant],
    paifuId: Option[PaifuId],
    matchRecordId: Option[MatchRecordId],
    appealTicketIds: Vector[AppealTicketId],
    resetCount: Int
) derives CanEqual

object TournamentTableView:
  def fromDomain(table: Table): TournamentTableView =
    TournamentTableView(
      tableId = table.id,
      tableNo = table.tableNo,
      tournamentId = table.tournamentId,
      stageId = table.stageId,
      seats = table.seats.map(TournamentTableSeatView.fromDomain),
      stageRoundNumber = table.stageRoundNumber,
      bracketMatchId = table.bracketMatchId,
      bracketRoundNumber = table.bracketRoundNumber,
      status = table.status,
      startedAt = table.startedAt,
      scoringStartedAt = table.scoringStartedAt,
      endedAt = table.endedAt,
      paifuId = table.paifuId,
      matchRecordId = table.matchRecordId,
      appealTicketIds = table.appealTicketIds,
      resetCount = table.resetCount
    )
