package riichinexus.microservices.tournament.domain.identity.functions

import riichinexus.microservices.tournament.domain.identity.model.TournamentIdPrefix
import riichinexus.microservices.tournament.objects.stage.lineup.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifu.PaifuId
import riichinexus.microservices.tournament.objects.matchrecord.MatchRecordId
import riichinexus.microservices.tournament.objects.finalization.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}

import java.util.UUID

/** TournamentIdGenerator 负责生成赛事标识符生成器 相关的领域标识符。 */

private[tournament] object TournamentIdGenerator:
  private def nextId(prefix: TournamentIdPrefix): String =
    s"${TournamentIdPrefix.toString(prefix)}-${UUID.randomUUID().toString.take(8)}"

  def tournamentId(): TournamentId = TournamentId(nextId(TournamentIdPrefix.Tournament))
  def stageId(): TournamentStageId = TournamentStageId(nextId(TournamentIdPrefix.Stage))
  def tableId(): TableId = TableId(nextId(TournamentIdPrefix.Table))
  def paifuId(): PaifuId = PaifuId(nextId(TournamentIdPrefix.Paifu))
  def matchRecordId(): MatchRecordId = MatchRecordId(nextId(TournamentIdPrefix.MatchRecord))
  def lineupSubmissionId(): LineupSubmissionId = LineupSubmissionId(nextId(TournamentIdPrefix.LineupSubmission))
  def settlementSnapshotId(): SettlementSnapshotId = SettlementSnapshotId(nextId(TournamentIdPrefix.SettlementSnapshot))
