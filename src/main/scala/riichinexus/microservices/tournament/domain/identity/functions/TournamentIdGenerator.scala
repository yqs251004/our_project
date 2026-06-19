package riichinexus.microservices.tournament.domain.identity.functions

import riichinexus.microservices.tournament.objects.stage.lineup.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifu.PaifuId
import riichinexus.microservices.tournament.objects.matchrecord.MatchRecordId
import riichinexus.microservices.tournament.objects.finalization.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}

import java.util.UUID

/** TournamentIdGenerator 负责生成赛事标识符生成器 相关的领域标识符。 */

private[tournament] object TournamentIdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def tournamentId(): TournamentId = TournamentId(nextId("tournament"))
  def stageId(): TournamentStageId = TournamentStageId(nextId("stage"))
  def tableId(): TableId = TableId(nextId("table"))
  def paifuId(): PaifuId = PaifuId(nextId("paifu"))
  def matchRecordId(): MatchRecordId = MatchRecordId(nextId("record"))
  def lineupSubmissionId(): LineupSubmissionId = LineupSubmissionId(nextId("lineup"))
  def settlementSnapshotId(): SettlementSnapshotId = SettlementSnapshotId(nextId("settlement"))
