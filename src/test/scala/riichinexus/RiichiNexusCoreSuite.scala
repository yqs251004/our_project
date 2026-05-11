package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.application.ports.GlobalDictionaryRepository
import riichinexus.application.service.PublicQueryService
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*

class RiichiNexusCoreSuite extends FunSuite with RiichiNexusSuiteSupport:

  test("guest access sessions can submit club applications with anonymous identity") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T17:00:00Z")

    val owner = app.playerService.registerPlayer("guest-owner", "GuestOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = app.clubService.createClub("Guest Friendly Club", owner.id, now, owner.asPrincipal)
    val session = app.guestSessionService.createSession("LobbyVisitor", now.plusSeconds(30))

    val application = app.clubService.applyForMembership(
      clubId = club.id,
      applicantUserId = Some(s"guest:${session.id.value}"),
      displayName = session.displayName,
      message = Some("watching first, joining soon"),
      submittedAt = now.plusSeconds(60),
      actor = AccessPrincipal.guest(session)
    ).getOrElse(fail("guest application missing"))

    assertEquals(app.guestSessionService.findSession(session.id), Some(session))
    assertEquals(application.displayName, "LobbyVisitor")
    assertEquals(application.applicantUserId, Some(s"guest:${session.id.value}"))
    assertEquals(
      app.clubRepository.findById(club.id).flatMap(_.membershipApplications.headOption).map(_.id),
      Some(application.id)
    )
  }

  test("pending club applications can be withdrawn by their original actor") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T17:10:00Z")

    val owner = app.playerService.registerPlayer("withdraw-owner", "WithdrawOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val applicant = app.playerService.registerPlayer("withdraw-player", "WithdrawPlayer", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val club = app.clubService.createClub("Withdraw Club", owner.id, now, owner.asPrincipal)

    val application = app.clubService.applyForMembership(
      clubId = club.id,
      applicantUserId = Some(applicant.userId),
      displayName = applicant.nickname,
      actor = applicant.asPrincipal,
      submittedAt = now.plusSeconds(30)
    ).getOrElse(fail("application missing"))

    val withdrawn = app.clubService.withdrawMembershipApplication(
      clubId = club.id,
      applicationId = application.id,
      actor = applicant.asPrincipal,
      withdrawnAt = now.plusSeconds(60),
      note = Some("changed my mind")
    ).getOrElse(fail("withdrawal missing"))

    assertEquals(withdrawn.status, ClubMembershipApplicationStatus.Withdrawn)
    assertEquals(withdrawn.withdrawnByPrincipalId, Some(applicant.id.value))
    assertEquals(withdrawn.reviewNote, Some("changed my mind"))
    assertEquals(
      app.clubRepository.findById(club.id).flatMap(_.findApplication(application.id)).map(_.status),
      Some(ClubMembershipApplicationStatus.Withdrawn)
    )
  }

  test("tournament persistence auto-provisions an initial stage for empty tournaments") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T17:20:00Z")

    val legacyTournament = Tournament(
      id = IdGenerator.tournamentId(),
      name = "Legacy Empty Stage Cup",
      organizer = "QA",
      startsAt = now,
      endsAt = now.plusSeconds(3600),
      stages = Vector.empty
    )

    val saved = app.tournamentRepository.save(legacyTournament)
    assertEquals(saved.stages.size, 1)
    assertEquals(saved.stages.head.name, "Swiss Stage 1")

    val reloaded = app.tournamentRepository.findById(saved.id).getOrElse(fail("tournament missing"))
    assertEquals(reloaded.stages.map(_.id), saved.stages.map(_.id))
    assertEquals(reloaded.stages.head.roundCount, 4)

    val published = app.tournamentService.publishTournament(saved.id)
      .getOrElse(fail("published tournament missing"))
    assertEquals(published.status, TournamentStatus.RegistrationOpen)
    assertEquals(published.stages.size, 1)
  }

  test("scheduling a stage creates one four-player table") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T09:00:00Z")

    val players = Vector(
      app.playerService.registerPlayer("u1", "A", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      app.playerService.registerPlayer("u2", "B", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580),
      app.playerService.registerPlayer("u3", "C", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1560),
      app.playerService.registerPlayer("u4", "D", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1540)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Swiss-1", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Test Cup",
      "QA",
      now,
      now.plusSeconds(3600),
      Vector(stage)
    )

    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id))
    app.tournamentService.publishTournament(tournament.id)

    val tables = app.tournamentService.scheduleStageTables(tournament.id, stage.id)

    assertEquals(tables.size, 1)
    assertEquals(tables.head.seats.size, 4)
    assertEquals(tables.head.seats.map(_.playerId).toSet, players.map(_.id).toSet)
  }

  test("recording a completed table updates player elo and dashboards") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T09:00:00Z")

    val alice = app.playerService.registerPlayer("u1", "Alice", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val bob = app.playerService.registerPlayer("u2", "Bob", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val charlie = app.playerService.registerPlayer("u3", "Charlie", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1560)
    val diana = app.playerService.registerPlayer("u4", "Diana", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1540)

    val stage = TournamentStage(IdGenerator.stageId(), "Swiss-1", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Projection Cup",
      "QA",
      now,
      now.plusSeconds(3600),
      Vector(stage)
    )

    Vector(alice, bob, charlie, diana).foreach { player =>
      app.tournamentService.registerPlayer(tournament.id, player.id)
    }
    app.tournamentService.publishTournament(tournament.id)

    val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id).head
    app.tableService.startTable(table.id, now.plusSeconds(60))

    val paifu = demoPaifu(table, tournament.id, stage.id, now.plusSeconds(120))
    app.tableService.recordCompletedTable(table.id, paifu)
    val pendingTasks = app.advancedStatsRecomputeTaskRepository.findPending(20)
    app.advancedStatsPipelineService.processPending(limit = 20, processedAt = now.plusSeconds(180))

    val updatedAlice = app.playerRepository.findById(alice.id).get
    val updatedBob = app.playerRepository.findById(bob.id).get
    val aliceDashboard = app.dashboardRepository.findByOwner(DashboardOwner.Player(alice.id))
    val bobDashboard = app.dashboardRepository.findByOwner(DashboardOwner.Player(bob.id))
    val aliceAdvancedStats = app.advancedStatsBoardRepository.findByOwner(DashboardOwner.Player(alice.id))
    val bobAdvancedStats = app.advancedStatsBoardRepository.findByOwner(DashboardOwner.Player(bob.id))

    assertNotEquals(updatedAlice.elo, alice.elo)
    assertNotEquals(updatedBob.elo, bob.elo)
    assert(pendingTasks.exists(_.owner == DashboardOwner.Player(alice.id)))
    assert(aliceDashboard.nonEmpty)
    assert(bobDashboard.nonEmpty)
    assert(aliceAdvancedStats.nonEmpty)
    assert(bobAdvancedStats.nonEmpty)
    assertEquals(app.tableRepository.findById(table.id).get.status, TableStatus.Finished)
  }

  test("advanced stats pipeline computes exact sample metadata from detailed paifu") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T03:00:00Z")

    val players = Vector(
      app.playerService.registerPlayer("exact-a", "ExactA", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700),
      app.playerService.registerPlayer("exact-b", "ExactB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      app.playerService.registerPlayer("exact-c", "ExactC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      app.playerService.registerPlayer("exact-d", "ExactD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1550)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Exact Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Exact Analytics Cup",
      "QA",
      now,
      now.plusSeconds(3600),
      Vector(stage)
    )
    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id))
    app.tournamentService.publishTournament(tournament.id)

    val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id).head
    val orderedSeats = table.seats.sortBy(_.seat.ordinal)
    val targetPlayer = orderedSeats.head.playerId

    app.tableService.startTable(table.id, now.plusSeconds(30))
    app.tableService.recordCompletedTable(
      table.id,
      detailedAnalyticsPaifu(table, tournament.id, stage.id, now.plusSeconds(60))
    )

    val board = eventually("expected advanced stats board") {
      app.advancedStatsBoardRepository.findByOwner(DashboardOwner.Player(targetPlayer))
    }
    val completedTask = eventually("expected advanced stats task completion") {
      app.advancedStatsRecomputeTaskRepository.findAll().find(task =>
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

    val task = app.advancedStatsRecomputeTaskRepository.save(
      AdvancedStatsRecomputeTask.create(
        owner = DashboardOwner.Club(ClubId("missing-club")),
        reason = "missing-club-backfill",
        requestedAt = now
      )
    )

    val firstAttempt = app.advancedStatsPipelineService.processPending(limit = 10, processedAt = now)
    val firstState = app.advancedStatsRecomputeTaskRepository.findById(task.id).getOrElse(fail("missing first task state"))
    assertEquals(firstAttempt.map(_.id), Vector(task.id))
    assertEquals(firstState.status, AdvancedStatsRecomputeTaskStatus.Pending)
    assertEquals(firstState.attempts, 1)
    assertEquals(firstState.nextAttemptAt, Some(now.plusSeconds(300)))

    val tooSoon = app.advancedStatsPipelineService.processPending(limit = 10, processedAt = now.plusSeconds(60))
    assertEquals(tooSoon, Vector.empty)

    app.advancedStatsPipelineService.processPending(limit = 10, processedAt = now.plusSeconds(300))
    val secondState = app.advancedStatsRecomputeTaskRepository.findById(task.id).getOrElse(fail("missing second task state"))
    assertEquals(secondState.status, AdvancedStatsRecomputeTaskStatus.Pending)
    assertEquals(secondState.attempts, 2)
    assertEquals(secondState.nextAttemptAt, Some(now.plusSeconds(600)))

    app.advancedStatsPipelineService.processPending(limit = 10, processedAt = now.plusSeconds(600))
    val finalState = app.advancedStatsRecomputeTaskRepository.findById(task.id).getOrElse(fail("missing final task state"))
    assertEquals(finalState.status, AdvancedStatsRecomputeTaskStatus.DeadLetter)
    assertEquals(finalState.attempts, 3)
    assert(finalState.lastError.exists(_.contains("missing-club")))

    val summary = app.advancedStatsPipelineService.taskQueueSummary(now.plusSeconds(600))
    assertEquals(summary.deadLetterCount, 1)
    assertEquals(summary.scheduledRetryCount, 0)
    assertEquals(summary.runnablePendingCount, 0)
  }

  test("advanced stats backfill mode targets only missing boards") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T03:40:00Z")

    val playerA = app.playerService.registerPlayer("backfill-a", "BackfillA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val playerB = app.playerService.registerPlayer("backfill-b", "BackfillB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    app.advancedStatsBoardRepository.save(AdvancedStatsBoard.empty(DashboardOwner.Player(playerA.id), now))

    val tasks = app.advancedStatsPipelineService.enqueueBackfill(
      mode = AdvancedStatsBackfillMode.Missing,
      requestedAt = now.plusSeconds(30),
      reason = "missing-only-backfill",
      limit = 10
    )

    assert(tasks.exists(_.owner == DashboardOwner.Player(playerB.id)))
    assert(!tasks.exists(_.owner == DashboardOwner.Player(playerA.id)))
  }

  test("stage lineups preserve represented club for multi-club players") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T10:00:00Z")

    val alpha = app.playerService.registerPlayer("u1", "Alpha", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val beta = app.playerService.registerPlayer("u2", "Beta", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val gamma = app.playerService.registerPlayer("u3", "Gamma", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1560)
    val delta = app.playerService.registerPlayer("u4", "Delta", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1540)

    val clubA = app.clubService.createClub("Club A", alpha.id, now, alpha.asPrincipal)
    val clubB = app.clubService.createClub("Club B", gamma.id, now, gamma.asPrincipal)
    val clubC = app.clubService.createClub("Club C", delta.id, now, delta.asPrincipal)

    app.clubService.addMember(clubA.id, beta.id, principalFor(app, alpha.id))
    app.clubService.addMember(clubB.id, alpha.id, principalFor(app, gamma.id))
    app.clubService.assignAdmin(clubB.id, alpha.id, principalFor(app, gamma.id))

    val stage = TournamentStage(IdGenerator.stageId(), "Lineup Swiss", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Club Representation Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    app.tournamentService.registerClub(tournament.id, clubA.id)
    app.tournamentService.registerClub(tournament.id, clubB.id)
    app.tournamentService.registerClub(tournament.id, clubC.id)
    app.tournamentService.publishTournament(tournament.id)

    app.tournamentService.submitLineup(
      tournament.id,
      stage.id,
      StageLineupSubmission(
        id = IdGenerator.lineupSubmissionId(),
        clubId = clubA.id,
        submittedBy = alpha.id,
        submittedAt = now,
        seats = Vector(StageLineupSeat(beta.id))
      ),
      principalFor(app, alpha.id)
    )
    app.tournamentService.submitLineup(
      tournament.id,
      stage.id,
      StageLineupSubmission(
        id = IdGenerator.lineupSubmissionId(),
        clubId = clubB.id,
        submittedBy = alpha.id,
        submittedAt = now,
        seats = Vector(StageLineupSeat(alpha.id), StageLineupSeat(gamma.id))
      ),
      principalFor(app, alpha.id)
    )
    app.tournamentService.submitLineup(
      tournament.id,
      stage.id,
      StageLineupSubmission(
        id = IdGenerator.lineupSubmissionId(),
        clubId = clubC.id,
        submittedBy = delta.id,
        submittedAt = now,
        seats = Vector(StageLineupSeat(delta.id))
      ),
      principalFor(app, delta.id)
    )

    val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id).head
    val alphaSeat = table.seats.find(_.playerId == alpha.id).getOrElse(fail("Alpha seat missing"))
    assertEquals(alphaSeat.clubId, Some(clubB.id))

    app.tableService.startTable(table.id, now.plusSeconds(60))
    app.tableService.recordCompletedTable(
      table.id,
      demoPaifuForResult(
        table,
        tournament.id,
        stage.id,
        now.plusSeconds(120),
        winner = alpha.id,
        target = beta.id
      )
    )

    assertEquals(app.clubRepository.findById(clubB.id).map(_.totalPoints), Some(7700))
    assertEquals(app.clubRepository.findById(clubA.id).map(_.totalPoints), Some(-7700))
  }

  test("secondary club dashboards refresh after a member match result") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T11:00:00Z")

    val alpha = app.playerService.registerPlayer("u1", "Alpha", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val beta = app.playerService.registerPlayer("u2", "Beta", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val gamma = app.playerService.registerPlayer("u3", "Gamma", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1560)
    val delta = app.playerService.registerPlayer("u4", "Delta", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1540)
    val epsilon = app.playerService.registerPlayer("u5", "Epsilon", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1520)

    app.clubService.createClub("Primary Club", alpha.id, now, alpha.asPrincipal)
    val secondaryClub = app.clubService.createClub("Secondary Club", epsilon.id, now, epsilon.asPrincipal)
    app.clubService.addMember(secondaryClub.id, alpha.id, principalFor(app, epsilon.id))

    val stage = TournamentStage(IdGenerator.stageId(), "Dashboard Swiss", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Dashboard Cup",
      "QA",
      now,
      now.plusSeconds(3600),
      Vector(stage)
    )

    Vector(alpha, beta, gamma, delta).foreach(player =>
      app.tournamentService.registerPlayer(tournament.id, player.id)
    )
    app.tournamentService.publishTournament(tournament.id)

    val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id).head
    app.tableService.startTable(table.id, now.plusSeconds(60))
    app.tableService.recordCompletedTable(
      table.id,
      demoPaifuForResult(
        table,
        tournament.id,
        stage.id,
        now.plusSeconds(120),
        winner = alpha.id,
        target = beta.id
      )
    )

    val dashboard = app.dashboardRepository.findByOwner(DashboardOwner.Club(secondaryClub.id))
    assert(dashboard.nonEmpty)
    assertEquals(dashboard.map(_.sampleSize), Some(1))
  }

  test("club titles can be cleared after assignment with audit trail") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T17:30:00Z")

    val owner = app.playerService.registerPlayer("title-owner", "TitleOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val member = app.playerService.registerPlayer("title-member", "TitleMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = app.clubService.createClub("Title Club", owner.id, now, owner.asPrincipal)
    app.clubService.addMember(club.id, member.id, principalFor(app, owner.id))

    app.clubService.setInternalTitle(
      club.id,
      member.id,
      "Vice Captain",
      principalFor(app, owner.id),
      now.plusSeconds(30),
      Some("promotion")
    )
    val clearedClub = app.clubService.clearInternalTitle(
      club.id,
      member.id,
      principalFor(app, owner.id),
      now.plusSeconds(60),
      Some("rotation")
    ).getOrElse(fail("cleared club missing"))

    assertEquals(clearedClub.titleAssignments, Vector.empty)
    val auditTypes = app.auditEventRepository.findByAggregate("club", club.id.value).map(_.eventType)
    assert(auditTypes.contains("ClubTitleAssigned"))
    assert(auditTypes.contains("ClubTitleCleared"))
  }

  test("registering players and managing club rosters initializes dashboards and power rating") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T12:00:00Z")

    val owner = app.playerService.registerPlayer("proj-owner", "Owner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val member = app.playerService.registerPlayer("proj-member", "Member", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)

    assertEquals(app.dashboardRepository.findByOwner(DashboardOwner.Player(owner.id)).map(_.sampleSize), Some(0))
    assertEquals(app.dashboardRepository.findByOwner(DashboardOwner.Player(member.id)).map(_.sampleSize), Some(0))

    val club = app.clubService.createClub("Projection Club", owner.id, now, owner.asPrincipal)
    val createdClub = app.clubRepository.findById(club.id).getOrElse(fail("club missing after creation"))
    assertEquals(createdClub.powerRating, 1800.0)
    assertEquals(app.dashboardRepository.findByOwner(DashboardOwner.Club(club.id)).map(_.sampleSize), Some(0))

    app.clubService.addMember(club.id, member.id, principalFor(app, owner.id))
    val expandedClub = app.clubRepository.findById(club.id).getOrElse(fail("club missing after add member"))
    assertEquals(expandedClub.powerRating, 1700.0)

    app.clubService.removeMember(club.id, member.id, principalFor(app, owner.id))
    val reducedClub = app.clubRepository.findById(club.id).getOrElse(fail("club missing after remove member"))
    assertEquals(reducedClub.powerRating, 1800.0)
  }

  test("global dictionary can tune club power formula at runtime") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T12:00:00Z")

    app.superAdminService.upsertDictionary(
      key = "club.power.baseBonus",
      value = "25",
      actor = AccessPrincipal.system,
      updatedAt = now
    )

    val owner = app.playerService.registerPlayer("dict-power-owner", "DictPowerOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val member = app.playerService.registerPlayer("dict-power-member", "DictPowerMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)

    val club = app.clubService.createClub("Dictionary Power Club", owner.id, now, owner.asPrincipal)
    assertEquals(app.clubRepository.findById(club.id).map(_.powerRating), Some(1825.0))

    app.clubService.addMember(club.id, member.id, principalFor(app, owner.id))
    assertEquals(app.clubRepository.findById(club.id).map(_.powerRating), Some(1725.0))
  }

  test("global dictionary can tune rating calculation at runtime") {
    def winnerDeltaWith(kFactor: Option[Int]): Int =
      val app = ApplicationContext.inMemory()
      val now = Instant.parse("2026-03-16T12:10:00Z")

      kFactor.foreach { value =>
        app.superAdminService.upsertDictionary(
          key = "rating.elo.kFactor",
          value = value.toString,
          actor = AccessPrincipal.system,
          updatedAt = now
        )
      }

      val players = Vector(
        app.playerService.registerPlayer("dict-elo-a", "DictA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
        app.playerService.registerPlayer("dict-elo-b", "DictB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580),
        app.playerService.registerPlayer("dict-elo-c", "DictC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1560),
        app.playerService.registerPlayer("dict-elo-d", "DictD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1540)
      )

      val stage = TournamentStage(IdGenerator.stageId(), "Dictionary Elo Stage", StageFormat.Swiss, 1, 1)
      val tournament = app.tournamentService.createTournament(
        "Dictionary Elo Cup",
        "QA",
        now,
        now.plusSeconds(3600),
        Vector(stage)
      )

      players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id))
      app.tournamentService.publishTournament(tournament.id)

      val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id).head
      val winnerId = table.seats.head.playerId
      app.tableService.startTable(table.id, now.plusSeconds(60))
      app.tableService.recordCompletedTable(
        table.id,
        demoPaifuForResult(
          table,
          tournament.id,
          stage.id,
          now.plusSeconds(120),
          winner = winnerId,
          target = table.seats(1).playerId
        )
      )

      app.playerRepository.findById(winnerId).getOrElse(fail("winner missing")).elo -
        players.find(_.id == winnerId).getOrElse(fail("baseline player missing")).elo

    val baselineDelta = winnerDeltaWith(None)
    val tunedDelta = winnerDeltaWith(Some(72))

    assert(tunedDelta > baselineDelta)
  }

  test("global dictionary provides default tournament payout ratios") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T12:20:00Z")

    app.superAdminService.upsertDictionary(
      key = "settlement.defaultPayoutRatios",
      value = "0.7,0.2,0.1",
      actor = AccessPrincipal.system,
      updatedAt = now
    )

    val admin = app.playerService.registerPlayer("dict-settle-admin", "DictSettleAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = Vector(
      admin,
      app.playerService.registerPlayer("dict-settle-b", "DictSettleB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      app.playerService.registerPlayer("dict-settle-c", "DictSettleC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      app.playerService.registerPlayer("dict-settle-d", "DictSettleD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Settlement Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Dictionary Settlement Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))

    val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    app.tableService.startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))
    app.tableService.recordCompletedTable(
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
    app.tournamentService.completeStage(
      tournament.id,
      stage.id,
      principalFor(app, admin.id),
      now.plusSeconds(180)
    )

    val settlement = app.tournamentService.settleTournament(
      tournamentId = tournament.id,
      finalStageId = stage.id,
      prizePool = 1000,
      payoutRatios = Vector.empty,
      actor = principalFor(app, admin.id),
      settledAt = now.plusSeconds(240)
    )

    assertEquals(settlement.entries.take(3).map(_.awardAmount), Vector(700L, 200L, 100L))
    val exportRecord = app.eventCascadeRecordRepository.findAll()
      .find(_.eventType == "TournamentSettlementRecorded")
      .getOrElse(fail("settlement export record missing"))
    assertEquals(exportRecord.consumer, EventCascadeConsumer.SettlementExport)
    assertEquals(exportRecord.status, EventCascadeStatus.Completed)
    assertEquals(exportRecord.aggregateId, settlement.id.value)
  }

  test("tournament settlement supports revisions draft finalization and club-share adjustments") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T13:10:00Z")

    val admin = app.playerService.registerPlayer("settle-admin", "SettleAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = Vector(
      admin,
      app.playerService.registerPlayer("settle-b", "SettleB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      app.playerService.registerPlayer("settle-c", "SettleC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      app.playerService.registerPlayer("settle-d", "SettleD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val club = app.clubService.createClub("Settlement Club", admin.id, now, admin.asPrincipal)
    players.tail.foreach(player => app.clubService.addMember(club.id, player.id, principalFor(app, admin.id)))

    val stage = TournamentStage(IdGenerator.stageId(), "Settlement Revision Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Settlement Revision Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))
    val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    app.tableService.startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))
    app.tableService.recordCompletedTable(
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
    app.tournamentService.completeStage(
      tournament.id,
      stage.id,
      principalFor(app, admin.id),
      now.plusSeconds(180)
    )

    val draft = app.tournamentService.settleTournament(
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

    val finalized = app.tournamentService.finalizeTournamentSettlement(
      tournamentId = tournament.id,
      settlementId = draft.id,
      actor = principalFor(app, admin.id),
      note = Some("approved for payout"),
      finalizedAt = now.plusSeconds(300)
    ).getOrElse(fail("finalized settlement missing"))
    assertEquals(finalized.status, TournamentSettlementStatus.Finalized)

    val revised = app.tournamentService.settleTournament(
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
    val supersededDraft = app.tournamentSettlementRepository.findById(draft.id).getOrElse(fail("superseded draft missing"))
    assertEquals(supersededDraft.status, TournamentSettlementStatus.Superseded)
    assertEquals(app.tournamentSettlementRepository.findByTournamentAndStage(tournament.id, stage.id).map(_.id), Some(revised.id))
  }

  test("appeal workflow emits moderation and notification cascade records") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T13:30:00Z")

    val admin = app.playerService.registerPlayer("appeal-admin", "AppealAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = Vector(
      admin,
      app.playerService.registerPlayer("appeal-b", "AppealB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      app.playerService.registerPlayer("appeal-c", "AppealC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      app.playerService.registerPlayer("appeal-d", "AppealD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Appeal Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Appeal Cascade Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))
    val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    app.tableService.startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))

    val ticket = app.appealService.fileAppeal(
      tableId = table.id,
      openedBy = table.seats.head.playerId,
      description = "score mismatch",
      actor = principalFor(app, table.seats.head.playerId),
      createdAt = now.plusSeconds(90)
    ).getOrElse(fail("appeal ticket missing"))

    app.appealService.resolveAppeal(
      ticketId = ticket.id,
      verdict = "restored prior state",
      actor = principalFor(app, admin.id),
      resolvedAt = now.plusSeconds(120)
    )

    val records = app.eventCascadeRecordRepository.findAll().filter(_.aggregateId == ticket.id.value)
    assert(records.exists(record => record.eventType == "AppealTicketFiled" && record.consumer == EventCascadeConsumer.ModerationInbox && record.status == EventCascadeStatus.Pending))
    assert(records.exists(record => record.eventType == "AppealTicketResolved" && record.consumer == EventCascadeConsumer.ModerationInbox && record.status == EventCascadeStatus.Completed))
    assert(records.exists(record => record.eventType == "AppealTicketAdjudicated" && record.consumer == EventCascadeConsumer.Notification && record.status == EventCascadeStatus.Completed))
  }

  test("appeal workflow supports triage assignment overdue tracking and reopening") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T13:40:00Z")

    val admin = app.playerService.registerPlayer("appeal-flow-admin", "AppealFlowAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1820)
    val players = Vector(
      admin,
      app.playerService.registerPlayer("appeal-flow-b", "AppealFlowB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      app.playerService.registerPlayer("appeal-flow-c", "AppealFlowC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      app.playerService.registerPlayer("appeal-flow-d", "AppealFlowD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Appeal Workflow Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Appeal Workflow Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))
    val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    app.tableService.startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))

    val ticket = app.appealService.fileAppeal(
      tableId = table.id,
      openedBy = table.seats.head.playerId,
      description = "ron points mismatch",
      priority = AppealPriority.High,
      dueAt = Some(now.plusSeconds(600)),
      actor = principalFor(app, table.seats.head.playerId),
      createdAt = now.plusSeconds(90)
    ).getOrElse(fail("appeal ticket missing"))
    assertEquals(ticket.priority, AppealPriority.High)

    val triaged = app.appealService.updateAppealWorkflow(
      ticketId = ticket.id,
      actor = principalFor(app, admin.id),
      assigneeId = Some(admin.id),
      priority = Some(AppealPriority.Critical),
      dueAt = Some(now.plusSeconds(300)),
      updatedAt = now.plusSeconds(120),
      note = Some("expedite finals ruling")
    ).getOrElse(fail("triaged appeal missing"))
    assertEquals(triaged.assigneeId, Some(admin.id))
    assertEquals(triaged.priority, AppealPriority.Critical)
    assertEquals(triaged.dueAt, Some(now.plusSeconds(300)))

    val rejected = app.appealService.adjudicateAppeal(
      ticketId = ticket.id,
      decision = AppealDecisionType.Reject,
      verdict = "log evidence insufficient",
      actor = principalFor(app, admin.id),
      adjudicatedAt = now.plusSeconds(180),
      note = Some("need clearer screenshot")
    ).getOrElse(fail("rejected appeal missing"))
    assertEquals(rejected.status, AppealStatus.Rejected)

    val reopened = app.appealService.reopenAppeal(
      ticketId = ticket.id,
      reason = "additional screenshot uploaded",
      actor = principalFor(app, table.seats.head.playerId),
      reopenedAt = now.plusSeconds(240),
      note = Some("new evidence available")
    ).getOrElse(fail("reopened appeal missing"))

    assertEquals(reopened.status, AppealStatus.Open)
    assertEquals(reopened.reopenCount, 1)
    assertEquals(reopened.assigneeId, Some(admin.id))
    assertEquals(app.tableRepository.findById(table.id).map(_.status), Some(TableStatus.AppealInProgress))

    val records = app.eventCascadeRecordRepository.findAll().filter(_.aggregateId == ticket.id.value)
    assert(records.exists(_.eventType == "AppealTicketWorkflowUpdated"))
    assert(records.exists(_.eventType == "AppealTicketReopened"))
    val auditTypes = app.auditEventRepository.findByAggregate("appeal", ticket.id.value).map(_.eventType)
    assert(auditTypes.contains("AppealTicketWorkflowUpdated"))
    assert(auditTypes.contains("AppealTicketReopened"))
  }

  test("dictionary updates and player bans repair club projections and emit cascade records") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T13:45:00Z")

    val owner = app.playerService.registerPlayer("repair-owner", "RepairOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val member = app.playerService.registerPlayer("repair-member", "RepairMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = app.clubService.createClub("Repair Club", owner.id, now, owner.asPrincipal)
    app.clubService.addMember(club.id, member.id, principalFor(app, owner.id))

    val powerBefore = app.clubRepository.findById(club.id).getOrElse(fail("club missing before update")).powerRating

    app.superAdminService.upsertDictionary(
      key = "club.power.baseBonus",
      value = "50",
      actor = AccessPrincipal.system,
      updatedAt = now.plusSeconds(30)
    )

    val boostedClub = app.clubRepository.findById(club.id).getOrElse(fail("club missing after dictionary update"))
    assert(boostedClub.powerRating > powerBefore)

    app.superAdminService.banPlayer(
      playerId = owner.id,
      reason = "rules violation",
      actor = AccessPrincipal.system,
      at = now.plusSeconds(60)
    )

    val repairedClub = app.clubRepository.findById(club.id).getOrElse(fail("club missing after ban"))
    assert(repairedClub.powerRating < boostedClub.powerRating)
    assertEquals(
      app.dashboardRepository.findByOwner(DashboardOwner.Player(owner.id)).map(_.sampleSize),
      Some(0)
    )
    assertEquals(
      app.advancedStatsBoardRepository.findByOwner(DashboardOwner.Player(owner.id)).map(_.sampleSize),
      Some(0)
    )

    val records = app.eventCascadeRecordRepository.findAll()
    val dictionaryRecord = records.find(_.eventType == "GlobalDictionaryUpdated").getOrElse(fail("dictionary cascade record missing"))
    assertEquals(dictionaryRecord.consumer, EventCascadeConsumer.ProjectionRepair)
    assertEquals(dictionaryRecord.metadata.get("repairedClubCount"), Some("1"))

    val banRecord = records.find(_.eventType == "PlayerBanned").getOrElse(fail("ban cascade record missing"))
    assertEquals(banRecord.consumer, EventCascadeConsumer.ProjectionRepair)
    assert(banRecord.metadata.getOrElse("repairedClubIds", "").contains(club.id.value))
  }

  test("dictionary namespace workflow governs metadata writes while runtime registry stays strict") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T15:00:00Z")

    val root = app.playerService.registerPlayer("dict-root", "DictRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = app.playerService.registerPlayer("dict-owner", "DictOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val outsider = app.playerService.registerPlayer("dict-outsider", "DictOutsider", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val superAdmin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))

    intercept[IllegalArgumentException] {
      app.superAdminService.upsertDictionary(
        key = "rating.elo.kFactor",
        value = "oops",
        actor = AccessPrincipal.system,
        updatedAt = now
      )
    }

    intercept[IllegalArgumentException] {
      app.superAdminService.upsertDictionary(
        key = "rating.elo.experimentalFactor",
        value = "72",
        actor = principalFor(app, superAdmin.id),
        updatedAt = now.plusSeconds(10)
      )
    }

    val pendingNamespace = app.superAdminService.requestDictionaryNamespace(
      namespacePrefix = "ui.banner",
      actor = owner.asPrincipal,
      requestedAt = now.plusSeconds(20),
      note = Some("frontend banners")
    )
    assertEquals(pendingNamespace.status, DictionaryNamespaceReviewStatus.Pending)

    intercept[IllegalArgumentException] {
      app.superAdminService.upsertDictionary(
        key = "ui.banner.message",
        value = "Spring finals this weekend",
        actor = owner.asPrincipal,
        updatedAt = now.plusSeconds(30)
      )
    }

    val approvedNamespace = app.superAdminService.reviewDictionaryNamespace(
      namespacePrefix = "ui.banner",
      approve = true,
      actor = principalFor(app, superAdmin.id),
      reviewedAt = now.plusSeconds(40),
      note = Some("approved for product copy")
    ).getOrElse(fail("namespace approval missing"))
    assertEquals(approvedNamespace.status, DictionaryNamespaceReviewStatus.Approved)

    val metadata = app.superAdminService.upsertDictionary(
      key = "ui.banner.message",
      value = "Spring finals this weekend",
      actor = owner.asPrincipal,
      updatedAt = now.plusSeconds(50)
    )

    intercept[IllegalArgumentException] {
      app.superAdminService.upsertDictionary(
        key = "ui.banner.message",
        value = "tampered",
        actor = outsider.asPrincipal,
        updatedAt = now.plusSeconds(60)
      )
    }

    val transferredNamespace = app.superAdminService.transferDictionaryNamespace(
      namespacePrefix = "ui.banner",
      newOwnerId = outsider.id,
      actor = principalFor(app, superAdmin.id),
      note = Some("product team handoff"),
      transferredAt = now.plusSeconds(70)
    ).getOrElse(fail("namespace transfer missing"))
    assertEquals(transferredNamespace.ownerPlayerId, outsider.id)
    assertEquals(transferredNamespace.status, DictionaryNamespaceReviewStatus.Approved)

    val formerOwnerWrite = app.superAdminService.upsertDictionary(
      key = "ui.banner.message",
      value = "former owner co-owner write",
      actor = owner.asPrincipal,
      updatedAt = now.plusSeconds(80)
    )

    val transferredWrite = app.superAdminService.upsertDictionary(
      key = "ui.banner.message",
      value = "Summer finals this weekend",
      actor = outsider.asPrincipal,
      updatedAt = now.plusSeconds(90)
    )

    val revokedNamespace = app.superAdminService.revokeDictionaryNamespace(
      namespacePrefix = "ui.banner",
      actor = principalFor(app, superAdmin.id),
      note = Some("namespace retired"),
      revokedAt = now.plusSeconds(100)
    ).getOrElse(fail("namespace revoke missing"))
    assertEquals(revokedNamespace.status, DictionaryNamespaceReviewStatus.Revoked)

    intercept[IllegalArgumentException] {
      app.superAdminService.upsertDictionary(
        key = "ui.banner.message",
        value = "revoked namespace blocked",
        actor = outsider.asPrincipal,
        updatedAt = now.plusSeconds(110)
      )
    }

    val namespaceAuditTypes = app.auditEventRepository.findByAggregate("dictionary-namespace", "ui.banner.").map(_.eventType)
    assert(namespaceAuditTypes.contains("DictionaryNamespaceTransferred"))
    assert(namespaceAuditTypes.contains("DictionaryNamespaceRevoked"))
    assertEquals(metadata.key, "ui.banner.message")
    assertEquals(metadata.value, "Spring finals this weekend")
    assertEquals(formerOwnerWrite.updatedBy, owner.id)
    assertEquals(transferredWrite.updatedBy, outsider.id)
  }

  test("dictionary namespace collaborators can write metadata and co-owners can manage editors") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T15:20:00Z")

    val root = app.playerService.registerPlayer("dict-collab-root", "DictCollabRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = app.playerService.registerPlayer("dict-collab-owner", "DictCollabOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val coOwner = app.playerService.registerPlayer("dict-collab-coowner", "DictCollabCoOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1590)
    val editor = app.playerService.registerPlayer("dict-collab-editor", "DictCollabEditor", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val replacementEditor = app.playerService.registerPlayer("dict-collab-replacement", "DictCollabReplacement", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1570)
    val outsider = app.playerService.registerPlayer("dict-collab-outsider", "DictCollabOutsider", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1560)
    val superAdmin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))

    val pending = app.superAdminService.requestDictionaryNamespace(
      namespacePrefix = "ui.banner",
      actor = owner.asPrincipal,
      coOwnerPlayerIds = Vector(coOwner.id),
      editorPlayerIds = Vector(editor.id),
      requestedAt = now.plusSeconds(10)
    )
    assertEquals(pending.coOwnerPlayerIds, Vector(coOwner.id))
    assertEquals(pending.editorPlayerIds, Vector(editor.id))

    app.superAdminService.reviewDictionaryNamespace(
      namespacePrefix = "ui.banner",
      approve = true,
      actor = principalFor(app, superAdmin.id),
      reviewedAt = now.plusSeconds(20)
    ).getOrElse(fail("namespace approval missing"))

    val coOwnerWrite = app.superAdminService.upsertDictionary(
      key = "ui.banner.message",
      value = "co-owner copy",
      actor = coOwner.asPrincipal,
      updatedAt = now.plusSeconds(30)
    )
    val editorWrite = app.superAdminService.upsertDictionary(
      key = "ui.banner.subtitle",
      value = "editor copy",
      actor = editor.asPrincipal,
      updatedAt = now.plusSeconds(40)
    )

    intercept[IllegalArgumentException] {
      app.superAdminService.upsertDictionary(
        key = "ui.banner.subtitle",
        value = "outsider blocked",
        actor = outsider.asPrincipal,
        updatedAt = now.plusSeconds(50)
      )
    }

    val updatedCollaborators = app.superAdminService.updateDictionaryNamespaceCollaborators(
      namespacePrefix = "ui.banner",
      coOwnerPlayerIds = Vector(coOwner.id),
      editorPlayerIds = Vector(replacementEditor.id),
      actor = coOwner.asPrincipal,
      note = Some("rotate content editor"),
      updatedAt = now.plusSeconds(60)
    ).getOrElse(fail("collaborator update missing"))
    assertEquals(updatedCollaborators.editorPlayerIds, Vector(replacementEditor.id))

    intercept[IllegalArgumentException] {
      app.superAdminService.upsertDictionary(
        key = "ui.banner.subtitle",
        value = "old editor blocked",
        actor = editor.asPrincipal,
        updatedAt = now.plusSeconds(70)
      )
    }

    val replacementWrite = app.superAdminService.upsertDictionary(
      key = "ui.banner.subtitle",
      value = "replacement editor copy",
      actor = replacementEditor.asPrincipal,
      updatedAt = now.plusSeconds(80)
    )

    val auditTypes = app.auditEventRepository.findByAggregate("dictionary-namespace", "ui.banner.").map(_.eventType)
    assert(auditTypes.contains("DictionaryNamespaceCollaboratorsUpdated"))
    assertEquals(coOwnerWrite.updatedBy, coOwner.id)
    assertEquals(editorWrite.updatedBy, editor.id)
    assertEquals(replacementWrite.updatedBy, replacementEditor.id)
  }

  test("dictionary namespace ownership rejects suspended or banned owners") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T16:30:00Z")

    val root = app.playerService.registerPlayer("dict-owner-safety-root", "OwnerSafetyRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = app.playerService.registerPlayer("dict-owner-safety-owner", "OwnerSafetyOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val suspended = app.playerService.registerPlayer("dict-owner-safety-suspended", "OwnerSafetySuspended", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val banned = app.playerService.registerPlayer("dict-owner-safety-banned", "OwnerSafetyBanned", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1490)
    val superAdmin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))
    app.playerRepository.save(suspended.copy(status = PlayerStatus.Suspended))
    app.playerRepository.save(banned.ban("policy violation"))

    intercept[IllegalArgumentException] {
      app.superAdminService.requestDictionaryNamespace(
        namespacePrefix = "ui.suspended-owner",
        actor = principalFor(app, superAdmin.id),
        ownerPlayerId = Some(suspended.id),
        requestedAt = now.plusSeconds(10)
      )
    }

    app.superAdminService.requestDictionaryNamespace(
      namespacePrefix = "ui.banner",
      actor = owner.asPrincipal,
      requestedAt = now.plusSeconds(20)
    )
    app.superAdminService.reviewDictionaryNamespace(
      namespacePrefix = "ui.banner",
      approve = true,
      actor = principalFor(app, superAdmin.id),
      reviewedAt = now.plusSeconds(30)
    ).getOrElse(fail("namespace approval missing"))

    intercept[IllegalArgumentException] {
      app.superAdminService.transferDictionaryNamespace(
        namespacePrefix = "ui.banner",
        newOwnerId = suspended.id,
        actor = principalFor(app, superAdmin.id),
        transferredAt = now.plusSeconds(40)
      )
    }

    intercept[IllegalArgumentException] {
      app.superAdminService.transferDictionaryNamespace(
        namespacePrefix = "ui.banner",
        newOwnerId = banned.id,
        actor = principalFor(app, superAdmin.id),
        transferredAt = now.plusSeconds(50)
      )
    }
  }

  test("dictionary namespace backlog tracks pending overdue and due-soon reviews") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T16:00:00Z")

    val root = app.playerService.registerPlayer("dict-backlog-root", "DictBacklogRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val ownerA = app.playerService.registerPlayer("dict-backlog-owner-a", "OwnerA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val ownerB = app.playerService.registerPlayer("dict-backlog-owner-b", "OwnerB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val superAdmin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))

    app.superAdminService.requestDictionaryNamespace(
      namespacePrefix = "ui.banner",
      actor = ownerA.asPrincipal,
      requestedAt = now,
      reviewDueAt = Some(now.plusSeconds(3600)),
      note = Some("banner family")
    )
    app.superAdminService.requestDictionaryNamespace(
      namespacePrefix = "ui.notice",
      actor = ownerA.asPrincipal,
      requestedAt = now.plusSeconds(60),
      reviewDueAt = Some(now.plusSeconds(8 * 3600)),
      note = Some("notice family")
    )
    app.superAdminService.requestDictionaryNamespace(
      namespacePrefix = "ui.card",
      actor = ownerB.asPrincipal,
      requestedAt = now.plusSeconds(120),
      reviewDueAt = Some(now.plusSeconds(72 * 3600)),
      note = Some("card family")
    )

    val backlog = app.superAdminService.dictionaryNamespaceBacklog(
      actor = principalFor(app, superAdmin.id),
      asOf = now.plusSeconds(5 * 3600),
      dueSoonWindow = java.time.Duration.ofHours(6)
    )

    assertEquals(backlog.pendingCount, 3)
    assertEquals(backlog.overdueCount, 1)
    assertEquals(backlog.dueSoonCount, 1)
    assertEquals(backlog.oldestPendingRequestedAt, Some(now))
    assertEquals(backlog.nextDueAt, Some(now.plusSeconds(3600)))
    assertEquals(backlog.ownerBacklog.map(_.ownerPlayerId), Vector(ownerA.id, ownerB.id))
    assertEquals(backlog.ownerBacklog.head.overdueCount, 1)
    assertEquals(backlog.ownerBacklog.head.dueSoonCount, 1)
  }

  test("dictionary namespace reminder processing emits due-soon and escalated actions without spamming") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T17:00:00Z")

    val root = app.playerService.registerPlayer("dict-reminder-root", "DictReminderRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val ownerA = app.playerService.registerPlayer("dict-reminder-owner-a", "DictReminderOwnerA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val ownerB = app.playerService.registerPlayer("dict-reminder-owner-b", "DictReminderOwnerB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1590)
    val superAdmin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))

    app.superAdminService.requestDictionaryNamespace(
      namespacePrefix = "ui.soon",
      actor = ownerA.asPrincipal,
      requestedAt = now.minusSeconds(600),
      reviewDueAt = Some(now.plusSeconds(2 * 3600))
    )
    app.superAdminService.requestDictionaryNamespace(
      namespacePrefix = "ui.legacy",
      actor = ownerB.asPrincipal,
      requestedAt = now.minusSeconds(120 * 3600),
      reviewDueAt = Some(now.minusSeconds(80 * 3600))
    )

    val firstBatch = app.superAdminService.processDictionaryNamespaceReminders(
      actor = principalFor(app, superAdmin.id),
      asOf = now,
      dueSoonWindow = java.time.Duration.ofHours(6),
      reminderInterval = java.time.Duration.ofHours(12),
      escalationGrace = java.time.Duration.ofHours(72)
    )
    assertEquals(firstBatch.map(_.reminderKind.toString).sorted, Vector(DictionaryNamespaceReminderKind.DueSoon.toString, DictionaryNamespaceReminderKind.Escalated.toString).sorted)
    assertEquals(firstBatch.map(_.namespacePrefix).sorted, Vector("ui.legacy.", "ui.soon."))

    val secondBatch = app.superAdminService.processDictionaryNamespaceReminders(
      actor = principalFor(app, superAdmin.id),
      asOf = now.plusSeconds(3600),
      dueSoonWindow = java.time.Duration.ofHours(6),
      reminderInterval = java.time.Duration.ofHours(12),
      escalationGrace = java.time.Duration.ofHours(72)
    )
    assertEquals(secondBatch, Vector.empty)

    val reminderEvents = app.auditEventRepository.findByAggregate("dictionary-namespace", "ui.legacy.").filter(_.eventType == "DictionaryNamespaceReminderTriggered")
    assertEquals(reminderEvents.size, 1)
  }

  test("dictionary namespace explicit context club governs collaborators transfers and writes") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T17:30:00Z")

    val root = app.playerService.registerPlayer("dict-context-root", "DictContextRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = app.playerService.registerPlayer("dict-context-owner", "DictContextOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val coOwner = app.playerService.registerPlayer("dict-context-coowner", "DictContextCoOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1590)
    val editor = app.playerService.registerPlayer("dict-context-editor", "DictContextEditor", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val outsider = app.playerService.registerPlayer("dict-context-outsider", "DictContextOutsider", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1570)
    val superAdmin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))

    val club = app.clubService.createClub("Namespace Context Club", owner.id, now, owner.asPrincipal)
    app.clubService.addMember(club.id, coOwner.id, principalFor(app, owner.id))
    app.clubService.addMember(club.id, editor.id, principalFor(app, owner.id))

    val pending = app.superAdminService.requestDictionaryNamespace(
      namespacePrefix = "ui.banner",
      actor = owner.asPrincipal,
      contextClubId = Some(club.id),
      coOwnerPlayerIds = Vector(coOwner.id),
      editorPlayerIds = Vector(editor.id),
      requestedAt = now.plusSeconds(10)
    )
    assertEquals(pending.contextClubId, Some(club.id))

    app.superAdminService.reviewDictionaryNamespace(
      namespacePrefix = "ui.banner",
      approve = true,
      actor = principalFor(app, superAdmin.id),
      reviewedAt = now.plusSeconds(20)
    ).getOrElse(fail("namespace approval missing"))

    val editorWrite = app.superAdminService.upsertDictionary(
      key = "ui.banner.message",
      value = "club editor write",
      actor = editor.asPrincipal,
      updatedAt = now.plusSeconds(30)
    )
    assertEquals(editorWrite.updatedBy, editor.id)

    intercept[IllegalArgumentException] {
      app.superAdminService.updateDictionaryNamespaceCollaborators(
        namespacePrefix = "ui.banner",
        coOwnerPlayerIds = Vector(coOwner.id),
        editorPlayerIds = Vector(outsider.id),
        actor = owner.asPrincipal,
        updatedAt = now.plusSeconds(40)
      )
    }

    intercept[IllegalArgumentException] {
      app.superAdminService.transferDictionaryNamespace(
        namespacePrefix = "ui.banner",
        newOwnerId = outsider.id,
        actor = principalFor(app, superAdmin.id),
        transferredAt = now.plusSeconds(50)
      )
    }

    app.clubService.removeMember(club.id, editor.id, principalFor(app, owner.id))

    intercept[IllegalArgumentException] {
      app.superAdminService.upsertDictionary(
        key = "ui.banner.message",
        value = "removed editor blocked",
        actor = editor.asPrincipal,
        updatedAt = now.plusSeconds(60)
      )
    }

    val contextCleared = app.superAdminService.updateDictionaryNamespaceContext(
      namespacePrefix = "ui.banner",
      contextClubId = None,
      actor = owner.asPrincipal,
      note = Some("decouple from club"),
      updatedAt = now.plusSeconds(70)
    ).getOrElse(fail("namespace context update missing"))
    assertEquals(contextCleared.contextClubId, None)

    val transferred = app.superAdminService.transferDictionaryNamespace(
      namespacePrefix = "ui.banner",
      newOwnerId = outsider.id,
      actor = principalFor(app, superAdmin.id),
      transferredAt = now.plusSeconds(80)
    ).getOrElse(fail("namespace transfer missing"))
    assertEquals(transferred.ownerPlayerId, outsider.id)

    val postClearWrite = app.superAdminService.upsertDictionary(
      key = "ui.banner.message",
      value = "editor restored after context clear",
      actor = editor.asPrincipal,
      updatedAt = now.plusSeconds(90)
    )
    assertEquals(postClearWrite.updatedBy, editor.id)

    val auditTypes = app.auditEventRepository.findByAggregate("dictionary-namespace", "ui.banner.").map(_.eventType)
    assert(auditTypes.contains("DictionaryNamespaceContextUpdated"))
  }


