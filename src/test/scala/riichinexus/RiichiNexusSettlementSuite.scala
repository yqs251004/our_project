package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*

class RiichiNexusSettlementSuite extends FunSuite with RiichiNexusSuiteSupport:

  test("global dictionary provides default tournament payout ratios") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T12:20:00Z")

    dictionaryGovernance(app).upsertDictionary(
      key = "settlement.defaultPayoutRatios",
      value = "0.7,0.2,0.1",
      actor = AccessPrincipal.system,
      updatedAt = now
    )

    val admin = playerService(app).registerPlayer("dict-settle-admin", "DictSettleAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("dict-settle-b", "DictSettleB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      playerService(app).registerPlayer("dict-settle-c", "DictSettleC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      playerService(app).registerPlayer("dict-settle-d", "DictSettleD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Settlement Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Dictionary Settlement Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))

    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    tableService(app).startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))
    tableService(app).recordCompletedTable(
      table.id,
      demoPaifuForResult(
        table,
        tournament.id,
        stage.id,
        now.plusSeconds(120),
        winner = table.seats.head.playerId,
        target = table.seats(1).playerId
      ),
      principalFor(app, admin.id)
    )
    tournamentService(app).completeStage(
      tournament.id,
      stage.id,
      principalFor(app, admin.id),
      now.plusSeconds(180)
    )

    val settlement = tournamentService(app).settleTournament(
      tournamentId = tournament.id,
      finalStageId = stage.id,
      prizePool = 1000,
      payoutRatios = Vector.empty,
      actor = principalFor(app, admin.id),
      settledAt = now.plusSeconds(240)
    )

    assertEquals(settlement.entries.take(3).map(_.awardAmount), Vector(700L, 200L, 100L))
    val exportRecord = eventCascadeRecordRepository(app).findAll()
      .find(_.eventType == "TournamentSettlementRecorded")
      .getOrElse(fail("settlement export record missing"))
    assertEquals(exportRecord.consumer, EventCascadeConsumer.SettlementExport)
    assertEquals(exportRecord.status, EventCascadeStatus.Completed)
    assertEquals(exportRecord.aggregateId, settlement.id.value)
  }

  test("tournament settlement supports revisions draft finalization and club-share adjustments") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T13:10:00Z")

    val admin = playerService(app).registerPlayer("settle-admin", "SettleAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("settle-b", "SettleB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      playerService(app).registerPlayer("settle-c", "SettleC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      playerService(app).registerPlayer("settle-d", "SettleD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val club = clubService(app).createClub("Settlement Club", admin.id, now, admin.asPrincipal)
    players.tail.foreach(player => clubService(app).addMember(club.id, player.id, principalFor(app, admin.id)))

    val stage = TournamentStage(IdGenerator.stageId(), "Settlement Revision Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Settlement Revision Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    tableService(app).startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))
    tableService(app).recordCompletedTable(
      table.id,
      demoPaifuForResult(
        table,
        tournament.id,
        stage.id,
        now.plusSeconds(120),
        winner = table.seats.head.playerId,
        target = table.seats(1).playerId
      ),
      principalFor(app, admin.id)
    )
    tournamentService(app).completeStage(
      tournament.id,
      stage.id,
      principalFor(app, admin.id),
      now.plusSeconds(180)
    )

    val draft = tournamentService(app).settleTournament(
      tournamentId = tournament.id,
      finalStageId = stage.id,
      prizePool = 1000,
      payoutRatios = Vector(0.6, 0.25, 0.15),
      houseFeeAmount = 100,
      clubShareRatio = 0.25,
      adjustments = Vector(
        TournamentSettlementAdjustment(players(1).id, "sportsmanship-award", 80L, Some("special recognition")),
        TournamentSettlementAdjustment(players(2).id, "late-penalty", -20L, Some("late check-in"))
      ),
      finalize = false,
      note = Some("initial draft settlement"),
      actor = principalFor(app, admin.id),
      settledAt = now.plusSeconds(240)
    )

    assertEquals(draft.status, TournamentSettlementStatus.Draft)
    assertEquals(draft.revision, 1)
    assertEquals(draft.houseFeeAmount, 100L)
    assertEquals(draft.netPrizePool, 900L)
    val championEntry = draft.entries.find(_.playerId == draft.championId).getOrElse(fail("champion entry missing"))
    val sportsmanshipEntry = draft.entries.find(_.playerId == players(1).id).getOrElse(fail("sportsmanship entry missing"))
    val penaltyEntry = draft.entries.find(_.playerId == players(2).id).getOrElse(fail("penalty entry missing"))
    assertEquals(championEntry.baseAwardAmount, 540L)
    assertEquals(sportsmanshipEntry.adjustmentAmount, 80L)
    assertEquals(penaltyEntry.deductionAmount, 20L)
    assertEquals(championEntry.clubShareAmount, 135L)

    val finalized = tournamentService(app).finalizeTournamentSettlement(
      tournamentId = tournament.id,
      settlementId = draft.id,
      actor = principalFor(app, admin.id),
      note = Some("approved for payout"),
      finalizedAt = now.plusSeconds(300)
    ).getOrElse(fail("finalized settlement missing"))
    assertEquals(finalized.status, TournamentSettlementStatus.Finalized)

    val revised = tournamentService(app).settleTournament(
      tournamentId = tournament.id,
      finalStageId = stage.id,
      prizePool = 1200,
      payoutRatios = Vector(0.5, 0.3, 0.2),
      houseFeeAmount = 120,
      clubShareRatio = 0.10,
      adjustments = Vector(TournamentSettlementAdjustment(players(3).id, "production-bonus", 50L)),
      finalize = true,
      note = Some("revised settlement"),
      actor = principalFor(app, admin.id),
      settledAt = now.plusSeconds(360)
    )

    assertEquals(revised.revision, 2)
    assertEquals(revised.status, TournamentSettlementStatus.Finalized)
    assertEquals(revised.supersedesSettlementId, Some(draft.id))
    val supersededDraft = tournamentSettlementRepository(app).findById(draft.id).getOrElse(fail("superseded draft missing"))
    assertEquals(supersededDraft.status, TournamentSettlementStatus.Superseded)
    assertEquals(tournamentSettlementRepository(app).findByTournamentAndStage(tournament.id, stage.id).map(_.id), Some(revised.id))
  }
