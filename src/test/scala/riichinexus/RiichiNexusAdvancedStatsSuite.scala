package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*

class RiichiNexusAdvancedStatsSuite extends FunSuite with RiichiNexusSuiteSupport:

  test("advanced stats pipeline computes exact sample metadata from detailed paifu") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T03:00:00Z")

    val players = Vector(
      playerService(app).registerPlayer("exact-a", "ExactA", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700),
      playerService(app).registerPlayer("exact-b", "ExactB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      playerService(app).registerPlayer("exact-c", "ExactC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      playerService(app).registerPlayer("exact-d", "ExactD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1550)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Exact Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Exact Analytics Cup",
      "QA",
      now,
      now.plusSeconds(3600),
      Vector(stage)
    )
    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id))
    tournamentService(app).publishTournament(tournament.id)

    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id).head
    val orderedSeats = table.seats.sortBy(_.seat.ordinal)
    val targetPlayer = orderedSeats.head.playerId

    tableService(app).startTable(table.id, now.plusSeconds(30))
    tableService(app).recordCompletedTable(
      table.id,
      detailedAnalyticsPaifu(table, tournament.id, stage.id, now.plusSeconds(60))
    )

    val board = eventually("expected advanced stats board") {
      advancedStatsBoardRepository(app).findByOwner(DashboardOwner.Player(targetPlayer))
    }
    val completedTask = eventually("expected advanced stats task completion") {
      advancedStatsRecomputeTaskRepository(app).findAll().find(task =>
        task.owner == DashboardOwner.Player(targetPlayer) &&
          task.status == AdvancedStatsRecomputeTaskStatus.Completed
      )
    }

    assertEquals(completedTask.status, AdvancedStatsRecomputeTaskStatus.Completed)
    assertEquals(board.calculatorVersion, AdvancedStatsBoard.CurrentCalculatorVersion)
    assert(board.strictRoundSampleSize > 0)
    assert(board.exactUkeireSampleRate > 0.0)
    assert(board.exactDefenseSampleRate > 0.0)
  }

  test("advanced stats pipeline retries failed tasks and dead-letters after max attempts") {
    val app = ApplicationContext.inMemory()
    val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)

    val task = advancedStatsRecomputeTaskRepository(app).save(
      AdvancedStatsRecomputeTask.create(
        owner = DashboardOwner.Club(ClubId("missing-club")),
        reason = "missing-club-backfill",
        requestedAt = now
      )
    )

    val firstAttempt = advancedStatsPipelineService(app).processPending(limit = 10, processedAt = now)
    val firstState = advancedStatsRecomputeTaskRepository(app).findById(task.id).getOrElse(fail("missing first task state"))
    assertEquals(firstAttempt.map(_.id), Vector(task.id))
    assertEquals(firstState.status, AdvancedStatsRecomputeTaskStatus.Pending)
    assertEquals(firstState.attempts, 1)
    assertEquals(firstState.nextAttemptAt, Some(now.plusSeconds(300)))

    val tooSoon = advancedStatsPipelineService(app).processPending(limit = 10, processedAt = now.plusSeconds(60))
    assertEquals(tooSoon, Vector.empty)

    advancedStatsPipelineService(app).processPending(limit = 10, processedAt = now.plusSeconds(300))
    val secondState = advancedStatsRecomputeTaskRepository(app).findById(task.id).getOrElse(fail("missing second task state"))
    assertEquals(secondState.status, AdvancedStatsRecomputeTaskStatus.Pending)
    assertEquals(secondState.attempts, 2)
    assertEquals(secondState.nextAttemptAt, Some(now.plusSeconds(600)))

    advancedStatsPipelineService(app).processPending(limit = 10, processedAt = now.plusSeconds(600))
    val finalState = advancedStatsRecomputeTaskRepository(app).findById(task.id).getOrElse(fail("missing final task state"))
    assertEquals(finalState.status, AdvancedStatsRecomputeTaskStatus.DeadLetter)
    assertEquals(finalState.attempts, 3)
    assert(finalState.lastError.exists(_.contains("missing-club")))

    val summary = advancedStatsPipelineService(app).taskQueueSummary(now.plusSeconds(600))
    assertEquals(summary.deadLetterCount, 1)
    assertEquals(summary.scheduledRetryCount, 0)
    assertEquals(summary.runnablePendingCount, 0)
  }

  test("advanced stats backfill mode targets only missing boards") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T03:40:00Z")

    val playerA = playerService(app).registerPlayer("backfill-a", "BackfillA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val playerB = playerService(app).registerPlayer("backfill-b", "BackfillB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    advancedStatsBoardRepository(app).save(AdvancedStatsBoard.empty(DashboardOwner.Player(playerA.id), now))

    val tasks = advancedStatsPipelineService(app).enqueueBackfill(
      mode = AdvancedStatsBackfillMode.Missing,
      requestedAt = now.plusSeconds(30),
      reason = "missing-only-backfill",
      limit = 10
    )

    assert(tasks.exists(_.owner == DashboardOwner.Player(playerB.id)))
    assert(!tasks.exists(_.owner == DashboardOwner.Player(playerA.id)))
  }
