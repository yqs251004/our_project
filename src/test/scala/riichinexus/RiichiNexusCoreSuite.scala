package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.event.PlayerBanned
import riichinexus.domain.model.*

class RiichiNexusCoreSuite extends FunSuite with RiichiNexusSuiteSupport:

  test("guest access sessions can submit club applications with anonymous identity") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T17:00:00Z")

    val owner = playerService(app).registerPlayer("guest-owner", "GuestOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = clubApi(app).createClub("Guest Friendly Club", owner.id, now, owner.asPrincipal)
    val session = createGuestSession(app, "LobbyVisitor")

    val application = clubApi(app).applyForMembership(
      clubId = club.id,
      applicantUserId = Some(s"guest:${session.id.value}"),
      displayName = session.displayName,
      message = Some("watching first, joining soon"),
      submittedAt = now.plusSeconds(60),
      actor = AccessPrincipal.guest(session)
    ).getOrElse(fail("guest application missing"))

    assertEquals(app.authModule.guestSessionRepository.findById(session.id), Some(session))
    assertEquals(application.displayName, "LobbyVisitor")
    assertEquals(application.applicantUserId, Some(s"guest:${session.id.value}"))
    assertEquals(
      clubRepository(app).findById(club.id).flatMap(_.membershipApplications.headOption).map(_.id),
      Some(application.id)
    )
  }

  test("pending club applications can be withdrawn by their original actor") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T17:10:00Z")

    val owner = playerService(app).registerPlayer("withdraw-owner", "WithdrawOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val applicant = playerService(app).registerPlayer("withdraw-player", "WithdrawPlayer", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val club = clubApi(app).createClub("Withdraw Club", owner.id, now, owner.asPrincipal)

    val application = clubApi(app).applyForMembership(
      clubId = club.id,
      applicantUserId = Some(applicant.userId),
      displayName = applicant.nickname,
      actor = applicant.asPrincipal,
      submittedAt = now.plusSeconds(30)
    ).getOrElse(fail("application missing"))

    val withdrawn = clubApi(app).withdrawMembershipApplication(
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
      clubRepository(app).findById(club.id).flatMap(_.findApplication(application.id)).map(_.status),
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

    val saved = tournamentRepository(app).save(legacyTournament)
    assertEquals(saved.stages.size, 1)
    assertEquals(saved.stages.head.name, "Swiss Stage 1")

    val reloaded = tournamentRepository(app).findById(saved.id).getOrElse(fail("tournament missing"))
    assertEquals(reloaded.stages.map(_.id), saved.stages.map(_.id))
    assertEquals(reloaded.stages.head.roundCount, 4)

    val published = tournamentService(app).publishTournament(saved.id)
      .getOrElse(fail("published tournament missing"))
    assertEquals(published.status, TournamentStatus.RegistrationOpen)
    assertEquals(published.stages.size, 1)
  }

  test("scheduling a stage creates one four-player table") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T09:00:00Z")

    val players = Vector(
      playerService(app).registerPlayer("u1", "A", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      playerService(app).registerPlayer("u2", "B", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580),
      playerService(app).registerPlayer("u3", "C", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1560),
      playerService(app).registerPlayer("u4", "D", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1540)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Swiss-1", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Test Cup",
      "QA",
      now,
      now.plusSeconds(3600),
      Vector(stage)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id))
    tournamentService(app).publishTournament(tournament.id)

    val tables = tournamentService(app).scheduleStageTables(tournament.id, stage.id)

    assertEquals(tables.size, 1)
    assertEquals(tables.head.seats.size, 4)
    assertEquals(tables.head.seats.map(_.playerId).toSet, players.map(_.id).toSet)
  }

  test("recording a completed table updates player elo and dashboards") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T09:00:00Z")

    val alice = playerService(app).registerPlayer("u1", "Alice", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val bob = playerService(app).registerPlayer("u2", "Bob", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val charlie = playerService(app).registerPlayer("u3", "Charlie", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1560)
    val diana = playerService(app).registerPlayer("u4", "Diana", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1540)

    val stage = TournamentStage(IdGenerator.stageId(), "Swiss-1", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Projection Cup",
      "QA",
      now,
      now.plusSeconds(3600),
      Vector(stage)
    )

    Vector(alice, bob, charlie, diana).foreach { player =>
      tournamentService(app).registerPlayer(tournament.id, player.id)
    }
    tournamentService(app).publishTournament(tournament.id)

    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id).head
    tableService(app).startTable(table.id, now.plusSeconds(60))

    val paifu = demoPaifu(table, tournament.id, stage.id, now.plusSeconds(120))
    tableService(app).recordCompletedTable(table.id, paifu)
    val advancedStatsTasks = advancedStatsRecomputeTaskRepository(app).findAll()

    val updatedAlice = playerRepository(app).findById(alice.id).get
    val updatedBob = playerRepository(app).findById(bob.id).get
    val aliceDashboard = dashboardRepository(app).findByOwner(DashboardOwner.Player(alice.id))
    val bobDashboard = dashboardRepository(app).findByOwner(DashboardOwner.Player(bob.id))
    val aliceAdvancedStats = advancedStatsBoardRepository(app).findByOwner(DashboardOwner.Player(alice.id))
    val bobAdvancedStats = advancedStatsBoardRepository(app).findByOwner(DashboardOwner.Player(bob.id))

    assertNotEquals(updatedAlice.elo, alice.elo)
    assertNotEquals(updatedBob.elo, bob.elo)
    assert(advancedStatsTasks.exists(_.owner == DashboardOwner.Player(alice.id)))
    assert(aliceDashboard.nonEmpty)
    assert(bobDashboard.nonEmpty)
    assert(aliceAdvancedStats.nonEmpty)
    assert(bobAdvancedStats.nonEmpty)
    assertEquals(tableRepository(app).findById(table.id).get.status, TableStatus.Finished)
  }

  test("stage lineups preserve represented club for multi-club players") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T10:00:00Z")

    val alpha = playerService(app).registerPlayer("u1", "Alpha", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val beta = playerService(app).registerPlayer("u2", "Beta", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val gamma = playerService(app).registerPlayer("u3", "Gamma", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1560)
    val delta = playerService(app).registerPlayer("u4", "Delta", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1540)

    val clubA = clubApi(app).createClub("Club A", alpha.id, now, alpha.asPrincipal)
    val clubB = clubApi(app).createClub("Club B", gamma.id, now, gamma.asPrincipal)
    val clubC = clubApi(app).createClub("Club C", delta.id, now, delta.asPrincipal)

    clubApi(app).addMember(clubA.id, beta.id, principalFor(app, alpha.id))
    clubApi(app).addMember(clubB.id, alpha.id, principalFor(app, gamma.id))
    clubApi(app).assignAdmin(clubB.id, alpha.id, principalFor(app, gamma.id))

    val stage = TournamentStage(IdGenerator.stageId(), "Lineup Swiss", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Club Representation Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    tournamentService(app).registerClub(tournament.id, clubA.id)
    tournamentService(app).registerClub(tournament.id, clubB.id)
    tournamentService(app).registerClub(tournament.id, clubC.id)
    tournamentService(app).publishTournament(tournament.id)

    tournamentService(app).submitLineup(
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
    tournamentService(app).submitLineup(
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
    tournamentService(app).submitLineup(
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

    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id).head
    val alphaSeat = table.seats.find(_.playerId == alpha.id).getOrElse(fail("Alpha seat missing"))
    assertEquals(alphaSeat.clubId, Some(clubB.id))

    tableService(app).startTable(table.id, now.plusSeconds(60))
    tableService(app).recordCompletedTable(
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

    assertEquals(clubRepository(app).findById(clubB.id).map(_.totalPoints), Some(7700))
    assertEquals(clubRepository(app).findById(clubA.id).map(_.totalPoints), Some(-7700))
  }

  test("secondary club dashboards refresh after a member match result") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T11:00:00Z")

    val alpha = playerService(app).registerPlayer("u1", "Alpha", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val beta = playerService(app).registerPlayer("u2", "Beta", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val gamma = playerService(app).registerPlayer("u3", "Gamma", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1560)
    val delta = playerService(app).registerPlayer("u4", "Delta", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1540)
    val epsilon = playerService(app).registerPlayer("u5", "Epsilon", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1520)

    clubApi(app).createClub("Primary Club", alpha.id, now, alpha.asPrincipal)
    val secondaryClub = clubApi(app).createClub("Secondary Club", epsilon.id, now, epsilon.asPrincipal)
    clubApi(app).addMember(secondaryClub.id, alpha.id, principalFor(app, epsilon.id))

    val stage = TournamentStage(IdGenerator.stageId(), "Dashboard Swiss", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Dashboard Cup",
      "QA",
      now,
      now.plusSeconds(3600),
      Vector(stage)
    )

    Vector(alpha, beta, gamma, delta).foreach(player =>
      tournamentService(app).registerPlayer(tournament.id, player.id)
    )
    tournamentService(app).publishTournament(tournament.id)

    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id).head
    tableService(app).startTable(table.id, now.plusSeconds(60))
    tableService(app).recordCompletedTable(
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

    val dashboard = dashboardRepository(app).findByOwner(DashboardOwner.Club(secondaryClub.id))
    assert(dashboard.nonEmpty)
    assertEquals(dashboard.map(_.sampleSize), Some(1))
  }

  test("club titles can be cleared after assignment with audit trail") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T17:30:00Z")

    val owner = playerService(app).registerPlayer("title-owner", "TitleOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val member = playerService(app).registerPlayer("title-member", "TitleMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = clubApi(app).createClub("Title Club", owner.id, now, owner.asPrincipal)
    clubApi(app).addMember(club.id, member.id, principalFor(app, owner.id))

    clubApi(app).setInternalTitle(
      club.id,
      member.id,
      "Vice Captain",
      principalFor(app, owner.id),
      now.plusSeconds(30),
      Some("promotion")
    )
    val clearedClub = clubApi(app).clearInternalTitle(
      club.id,
      member.id,
      principalFor(app, owner.id),
      now.plusSeconds(60),
      Some("rotation")
    ).getOrElse(fail("cleared club missing"))

    assertEquals(clearedClub.titleAssignments, Vector.empty)
    val auditTypes = auditEventRepository(app).findByAggregate("club", club.id.value).map(_.eventType)
    assert(auditTypes.contains("ClubTitleAssigned"))
    assert(auditTypes.contains("ClubTitleCleared"))
  }

  test("registering players and managing club rosters initializes dashboards and power rating") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T12:00:00Z")

    val owner = playerService(app).registerPlayer("proj-owner", "Owner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val member = playerService(app).registerPlayer("proj-member", "Member", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)

    assertEquals(dashboardRepository(app).findByOwner(DashboardOwner.Player(owner.id)).map(_.sampleSize), Some(0))
    assertEquals(dashboardRepository(app).findByOwner(DashboardOwner.Player(member.id)).map(_.sampleSize), Some(0))

    val club = clubApi(app).createClub("Projection Club", owner.id, now, owner.asPrincipal)
    val createdClub = clubRepository(app).findById(club.id).getOrElse(fail("club missing after creation"))
    assertEquals(createdClub.powerRating, 1800.0)
    assertEquals(dashboardRepository(app).findByOwner(DashboardOwner.Club(club.id)).map(_.sampleSize), Some(0))

    clubApi(app).addMember(club.id, member.id, principalFor(app, owner.id))
    val expandedClub = clubRepository(app).findById(club.id).getOrElse(fail("club missing after add member"))
    assertEquals(expandedClub.powerRating, 1700.0)

    clubApi(app).removeMember(club.id, member.id, principalFor(app, owner.id))
    val reducedClub = clubRepository(app).findById(club.id).getOrElse(fail("club missing after remove member"))
    assertEquals(reducedClub.powerRating, 1800.0)
  }

  test("global dictionary can tune club power formula at runtime") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T12:00:00Z")

    dictionaryApi(app).upsertDictionary(
      key = "club.power.baseBonus",
      value = "25",
      actor = AccessPrincipal.system,
      updatedAt = now
    )

    val owner = playerService(app).registerPlayer("dict-power-owner", "DictPowerOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val member = playerService(app).registerPlayer("dict-power-member", "DictPowerMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)

    val club = clubApi(app).createClub("Dictionary Power Club", owner.id, now, owner.asPrincipal)
    assertEquals(clubRepository(app).findById(club.id).map(_.powerRating), Some(1825.0))

    clubApi(app).addMember(club.id, member.id, principalFor(app, owner.id))
    assertEquals(clubRepository(app).findById(club.id).map(_.powerRating), Some(1725.0))
  }

  test("global dictionary can tune rating calculation at runtime") {
    def winnerDeltaWith(kFactor: Option[Int]): Int =
      val app = ApplicationContext.inMemory()
      val now = Instant.parse("2026-03-16T12:10:00Z")

      kFactor.foreach { value =>
        dictionaryApi(app).upsertDictionary(
          key = "rating.elo.kFactor",
          value = value.toString,
          actor = AccessPrincipal.system,
          updatedAt = now
        )
      }

      val players = Vector(
        playerService(app).registerPlayer("dict-elo-a", "DictA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
        playerService(app).registerPlayer("dict-elo-b", "DictB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580),
        playerService(app).registerPlayer("dict-elo-c", "DictC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1560),
        playerService(app).registerPlayer("dict-elo-d", "DictD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1540)
      )

      val stage = TournamentStage(IdGenerator.stageId(), "Dictionary Elo Stage", StageFormat.Swiss, 1, 1)
      val tournament = tournamentService(app).createTournament(
        "Dictionary Elo Cup",
        "QA",
        now,
        now.plusSeconds(3600),
        Vector(stage)
      )

      players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id))
      tournamentService(app).publishTournament(tournament.id)

      val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id).head
      val winnerId = table.seats.head.playerId
      tableService(app).startTable(table.id, now.plusSeconds(60))
      tableService(app).recordCompletedTable(
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

      playerRepository(app).findById(winnerId).getOrElse(fail("winner missing")).elo -
        players.find(_.id == winnerId).getOrElse(fail("baseline player missing")).elo

    val baselineDelta = winnerDeltaWith(None)
    val tunedDelta = winnerDeltaWith(Some(72))

    assert(tunedDelta > baselineDelta)
  }

  test("dictionary updates and player bans repair club projections and emit cascade records") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T13:45:00Z")

    val owner = playerService(app).registerPlayer("repair-owner", "RepairOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val member = playerService(app).registerPlayer("repair-member", "RepairMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = clubApi(app).createClub("Repair Club", owner.id, now, owner.asPrincipal)
    clubApi(app).addMember(club.id, member.id, principalFor(app, owner.id))

    val powerBefore = clubRepository(app).findById(club.id).getOrElse(fail("club missing before update")).powerRating

    dictionaryApi(app).upsertDictionary(
      key = "club.power.baseBonus",
      value = "50",
      actor = AccessPrincipal.system,
      updatedAt = now.plusSeconds(30)
    )

    val boostedClub = clubRepository(app).findById(club.id).getOrElse(fail("club missing after dictionary update"))
    assert(boostedClub.powerRating > powerBefore)

    val currentOwner = playerRepository(app).findById(owner.id).getOrElse(fail("owner missing before ban"))
    playerRepository(app).save(currentOwner.ban("rules violation"))
    app.platformAdminModule.eventBus.publish(
      PlayerBanned(owner.id, "rules violation", now.plusSeconds(60))
    )

    val repairedClub = clubRepository(app).findById(club.id).getOrElse(fail("club missing after ban"))
    assert(repairedClub.powerRating < boostedClub.powerRating)
    assertEquals(
      dashboardRepository(app).findByOwner(DashboardOwner.Player(owner.id)).map(_.sampleSize),
      Some(0)
    )
    assertEquals(
      advancedStatsBoardRepository(app).findByOwner(DashboardOwner.Player(owner.id)).map(_.sampleSize),
      Some(0)
    )

    val records = eventCascadeRecordRepository(app).findAll()
    val dictionaryRecord = records.find(_.eventType == "GlobalDictionaryUpdated").getOrElse(fail("dictionary cascade record missing"))
    assertEquals(dictionaryRecord.consumer, EventCascadeConsumer.ProjectionRepair)
    assertEquals(dictionaryRecord.metadata.get("repairedClubCount"), Some("1"))

    val banRecord = records.find(_.eventType == "PlayerBanned").getOrElse(fail("ban cascade record missing"))
    assertEquals(banRecord.consumer, EventCascadeConsumer.ProjectionRepair)
    assert(banRecord.metadata.getOrElse("repairedClubIds", "").contains(club.id.value))
  }

