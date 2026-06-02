package riichinexus.microservices.tournament.domain.functions

import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}

import java.util.UUID

object TournamentIdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def tournamentId(): TournamentId = TournamentId(nextId("tournament"))
  def stageId(): TournamentStageId = TournamentStageId(nextId("stage"))
  def tableId(): TableId = TableId(nextId("table"))
  def paifuId(): PaifuId = PaifuId(nextId("paifu"))
  def matchRecordId(): MatchRecordId = MatchRecordId(nextId("record"))
  def lineupSubmissionId(): LineupSubmissionId = LineupSubmissionId(nextId("lineup"))
  def settlementSnapshotId(): SettlementSnapshotId = SettlementSnapshotId(nextId("settlement"))
