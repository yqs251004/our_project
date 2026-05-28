package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.model.Table
import riichinexus.microservices.tournament.objects.TableStatus
import upickle.default.*

final case class TournamentTableView(
    tableId: String,
    tableNo: Int,
    tournamentId: String,
    stageId: String,
    seats: Vector[TournamentTableSeatView],
    stageRoundNumber: Int,
    bracketMatchId: Option[String],
    bracketRoundNumber: Option[Int],
    status: TableStatus,
    startedAt: Option[String],
    scoringStartedAt: Option[String],
    endedAt: Option[String],
    paifuId: Option[String],
    matchRecordId: Option[String],
    appealTicketIds: Vector[String],
    resetCount: Int
) derives CanEqual

object TournamentTableView:
  def fromDomain(table: Table): TournamentTableView =
    TournamentTableView(
      tableId = table.id.value,
      tableNo = table.tableNo,
      tournamentId = table.tournamentId.value,
      stageId = table.stageId.value,
      seats = table.seats.map(TournamentTableSeatView.fromDomain),
      stageRoundNumber = table.stageRoundNumber,
      bracketMatchId = table.bracketMatchId,
      bracketRoundNumber = table.bracketRoundNumber,
      status = TableStatus.fromDomain(table.status),
      startedAt = table.startedAt.map(_.toString),
      scoringStartedAt = table.scoringStartedAt.map(_.toString),
      endedAt = table.endedAt.map(_.toString),
      paifuId = table.paifuId.map(_.value),
      matchRecordId = table.matchRecordId.map(_.value),
      appealTicketIds = table.appealTicketIds.map(_.value),
      resetCount = table.resetCount
    )

  given ReadWriter[TournamentTableView] = macroRW
