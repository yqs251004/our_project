package riichinexus.microservices.tournament.domain.competition.functions

import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.domain.finalization.model.TournamentSettlementSnapshot
import riichinexus.microservices.tournament.domain.matchrecord.model.{MatchRecord, MatchRecordSeatResult}
import riichinexus.microservices.tournament.domain.stage.model.{Table, TournamentStage}
import riichinexus.microservices.tournament.objects.competition.TournamentSummaryView
import riichinexus.microservices.tournament.objects.finalization.TournamentSettlementView
import riichinexus.microservices.tournament.objects.matchrecord.{TournamentMatchRecordSeatResultView, TournamentMatchRecordView}
import riichinexus.microservices.tournament.objects.stage.TournamentStageSummaryView
import riichinexus.microservices.tournament.objects.stage.table.TournamentTableView

private[tournament] object TournamentViewFunctions:
  def tournamentSummaryView(tournament: Tournament): TournamentSummaryView =
    TournamentSummaryView(
      tournamentId = tournament.id.value,
      name = tournament.name,
      organizer = tournament.organizer,
      startsAt = tournament.startsAt.toString,
      endsAt = tournament.endsAt.toString,
      status = tournament.status,
      participatingClubIds = tournament.participatingClubs.map(_.value),
      participatingPlayerIds = tournament.participatingPlayers.map(_.value),
      adminIds = tournament.admins.map(_.value),
      whitelistCount = tournament.whitelist.size,
      stages = tournament.stages.sortBy(_.order).map(stageSummaryView)
    )

  def stageSummaryView(stage: TournamentStage): TournamentStageSummaryView =
    TournamentStageSummaryView(
      stageId = stage.id.value,
      name = stage.name,
      format = stage.format,
      order = stage.order,
      status = stage.status,
      currentRound = stage.currentRound,
      roundCount = stage.roundCount,
      schedulingPoolSize = stage.schedulingPoolSize,
      pendingTablePlanCount = stage.pendingTablePlans.size,
      scheduledTableCount = stage.scheduledTableIds.size,
      advancementRule = stage.advancementRule,
      swissRule = stage.swissRule,
      knockoutRule = stage.knockoutRule,
      mahjongRuleset = stage.mahjongRuleset
    )

  def tableView(table: Table): TournamentTableView =
    TournamentTableView(
      tableId = table.id.value,
      tableNo = table.tableNo,
      tournamentId = table.tournamentId.value,
      stageId = table.stageId.value,
      seats = table.seats,
      stageRoundNumber = table.stageRoundNumber,
      bracketMatchId = table.bracketMatchId,
      bracketRoundNumber = table.bracketRoundNumber,
      status = table.status,
      startedAt = table.startedAt.map(_.toString),
      scoringStartedAt = table.scoringStartedAt.map(_.toString),
      endedAt = table.endedAt.map(_.toString),
      paifuId = table.paifuId.map(_.value),
      matchRecordId = table.matchRecordId.map(_.value),
      appealTicketIds = table.appealTicketIds.map(_.value),
      resetCount = table.resetCount
    )

  def matchRecordView(record: MatchRecord): TournamentMatchRecordView =
    TournamentMatchRecordView(
      recordId = record.id.value,
      tableId = record.tableId.value,
      tournamentId = record.tournamentId.value,
      stageId = record.stageId.value,
      stageRoundNumber = record.stageRoundNumber,
      generatedAt = record.generatedAt.toString,
      seatResults = record.seatResults.map(matchRecordSeatResultView),
      paifuId = record.paifuId.map(_.value),
      finalizedBy = record.finalizedBy.map(_.value),
      sourceEvent = record.sourceEvent,
      notes = record.notes
    )

  def matchRecordSeatResultView(result: MatchRecordSeatResult): TournamentMatchRecordSeatResultView =
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

  def settlementView(snapshot: TournamentSettlementSnapshot): TournamentSettlementView =
    TournamentSettlementView(
      settlementId = snapshot.id.value,
      tournamentId = snapshot.tournamentId.value,
      stageId = snapshot.stageId.value,
      revision = snapshot.revision,
      status = snapshot.status,
      generatedAt = snapshot.generatedAt.toString,
      finalizedAt = snapshot.finalizedAt.map(_.toString),
      supersededAt = snapshot.supersededAt.map(_.toString),
      supersedesSettlementId = snapshot.supersedesSettlementId.map(_.value),
      championId = snapshot.championId.value,
      prizePool = snapshot.prizePool,
      houseFeeAmount = snapshot.houseFeeAmount,
      netPrizePool = snapshot.netPrizePool,
      clubShareRatio = snapshot.clubShareRatio,
      adjustments = snapshot.adjustments,
      entries = snapshot.entries,
      summary = snapshot.summary
    )
