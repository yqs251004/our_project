package riichinexus.microservices.tournament.objects

import riichinexus.microservices.tournament.domain.model.{Table as DomainTable}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class Table(
    id: String,
    tableNo: Int,
    tournamentId: String,
    stageId: String,
    seats: Vector[TableSeat],
    stageRoundNumber: Int,
    bracketMatchId: Option[String],
    bracketRoundNumber: Option[Int],
    feederMatchIds: Vector[String],
    status: String,
    startedAt: Option[String],
    scoringStartedAt: Option[String],
    endedAt: Option[String],
    paifuId: Option[String],
    matchRecordId: Option[String],
    appealTicketIds: Vector[String],
    resetCount: Int,
    operatorNotes: Vector[String],
    version: Int
) derives ReadWriter

object Table:
  def fromDomain(table: DomainTable): Table =
    Table(
      id = table.id.value,
      tableNo = table.tableNo,
      tournamentId = table.tournamentId.value,
      stageId = table.stageId.value,
      seats = table.seats.map(TableSeat.fromDomain),
      stageRoundNumber = table.stageRoundNumber,
      bracketMatchId = table.bracketMatchId,
      bracketRoundNumber = table.bracketRoundNumber,
      feederMatchIds = table.feederMatchIds,
      status = table.status.toString,
      startedAt = table.startedAt.map(_.toString),
      scoringStartedAt = table.scoringStartedAt.map(_.toString),
      endedAt = table.endedAt.map(_.toString),
      paifuId = table.paifuId.map(_.value),
      matchRecordId = table.matchRecordId.map(_.value),
      appealTicketIds = table.appealTicketIds.map(_.value),
      resetCount = table.resetCount,
      operatorNotes = table.operatorNotes,
      version = table.version
    )
