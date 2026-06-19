package riichinexus.microservices.tournament.objects.recordmanagement.apiTypes

import riichinexus.microservices.tournament.domain.matchrecord.model.MatchRecord
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** TournamentMatchRecordView 表示赛事对局记录视图 的前端展示视图。 */

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
)

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

  given ReadWriter[TournamentMatchRecordView] = macroRW

