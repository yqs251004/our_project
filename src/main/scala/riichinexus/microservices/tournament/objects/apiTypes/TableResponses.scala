package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*

final case class TournamentTableSeatView(
    seat: String,
    playerId: String,
    initialPoints: Int,
    disconnected: Boolean,
    ready: Boolean,
    clubId: Option[String]
) derives CanEqual

object TournamentTableSeatView:
  def fromDomain(seat: riichinexus.domain.model.TableSeat): TournamentTableSeatView =
    TournamentTableSeatView(
      seat = seat.seat.toString,
      playerId = seat.playerId.value,
      initialPoints = seat.initialPoints,
      disconnected = seat.disconnected,
      ready = seat.ready,
      clubId = seat.clubId.map(_.value)
    )

final case class TournamentTableView(
    tableId: String,
    tableNo: Int,
    tournamentId: String,
    stageId: String,
    seats: Vector[TournamentTableSeatView],
    stageRoundNumber: Int,
    bracketMatchId: Option[String],
    bracketRoundNumber: Option[Int],
    status: String,
    startedAt: Option[String],
    scoringStartedAt: Option[String],
    endedAt: Option[String],
    paifuId: Option[String],
    matchRecordId: Option[String],
    appealTicketIds: Vector[String],
    resetCount: Int
) derives CanEqual

object TournamentTableView:
  def fromDomain(table: riichinexus.domain.model.Table): TournamentTableView =
    TournamentTableView(
      tableId = table.id.value,
      tableNo = table.tableNo,
      tournamentId = table.tournamentId.value,
      stageId = table.stageId.value,
      seats = table.seats.map(TournamentTableSeatView.fromDomain),
      stageRoundNumber = table.stageRoundNumber,
      bracketMatchId = table.bracketMatchId,
      bracketRoundNumber = table.bracketRoundNumber,
      status = table.status.toString,
      startedAt = table.startedAt.map(_.toString),
      scoringStartedAt = table.scoringStartedAt.map(_.toString),
      endedAt = table.endedAt.map(_.toString),
      paifuId = table.paifuId.map(_.value),
      matchRecordId = table.matchRecordId.map(_.value),
      appealTicketIds = table.appealTicketIds.map(_.value),
      resetCount = table.resetCount
    )
