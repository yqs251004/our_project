package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*

class RiichiNexusSuite extends FunSuite:
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

    val updatedAlice = app.playerRepository.findById(alice.id).get
    val updatedBob = app.playerRepository.findById(bob.id).get
    val aliceDashboard = app.dashboardRepository.findByOwner(DashboardOwner.Player(alice.id))
    val bobDashboard = app.dashboardRepository.findByOwner(DashboardOwner.Player(bob.id))

    assertNotEquals(updatedAlice.elo, alice.elo)
    assertNotEquals(updatedBob.elo, bob.elo)
    assert(aliceDashboard.nonEmpty)
    assert(bobDashboard.nonEmpty)
    assertEquals(app.tableRepository.findById(table.id).get.status, TableStatus.Finished)
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

  test("stage lineup preferred winds influence scheduled seats") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T13:00:00Z")

    val owner = app.playerService.registerPlayer("wind-owner", "Owner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val south = app.playerService.registerPlayer("wind-south", "South", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700)
    val west = app.playerService.registerPlayer("wind-west", "West", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val north = app.playerService.registerPlayer("wind-north", "North", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)

    val club = app.clubService.createClub("Preferred Wind Club", owner.id, now, owner.asPrincipal)
    Vector(south, west, north).foreach(player =>
      app.clubService.addMember(club.id, player.id, principalFor(app, owner.id))
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Preferred Wind Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Preferred Wind Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    app.tournamentService.registerClub(tournament.id, club.id)
    app.tournamentService.publishTournament(tournament.id)
    app.tournamentService.submitLineup(
      tournament.id,
      stage.id,
      StageLineupSubmission(
        id = IdGenerator.lineupSubmissionId(),
        clubId = club.id,
        submittedBy = owner.id,
        submittedAt = now,
        seats = Vector(
          StageLineupSeat(owner.id, preferredWind = Some(SeatWind.East)),
          StageLineupSeat(south.id, preferredWind = Some(SeatWind.South)),
          StageLineupSeat(west.id, preferredWind = Some(SeatWind.West)),
          StageLineupSeat(north.id, preferredWind = Some(SeatWind.North))
        )
      ),
      principalFor(app, owner.id)
    )

    val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id).head
    val seatByPlayer = table.seats.map(seat => seat.playerId -> seat.seat).toMap

    assertEquals(seatByPlayer(owner.id), SeatWind.East)
    assertEquals(seatByPlayer(south.id), SeatWind.South)
    assertEquals(seatByPlayer(west.id), SeatWind.West)
    assertEquals(seatByPlayer(north.id), SeatWind.North)
  }

  test("club operations update treasury point pool and rank tree") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T14:00:00Z")

    val owner = app.playerService.registerPlayer("club-ops-owner", "Owner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val club = app.clubService.createClub("Operations Club", owner.id, now, owner.asPrincipal)

    val afterTreasury = app.clubService.adjustTreasury(
      club.id,
      delta = 5000L,
      actor = principalFor(app, owner.id),
      occurredAt = now.plusSeconds(60),
      note = Some("sponsor payment")
    ).getOrElse(fail("treasury update failed"))
    assertEquals(afterTreasury.treasuryBalance, 5000L)

    val afterPointPool = app.clubService.adjustPointPool(
      club.id,
      delta = 320,
      actor = principalFor(app, owner.id),
      occurredAt = now.plusSeconds(120),
      note = Some("internal event reward")
    ).getOrElse(fail("point pool update failed"))
    assertEquals(afterPointPool.pointPool, 320)

    val updatedRankTree = app.clubService.updateRankTree(
      club.id,
      rankTree = Vector(
        ClubRankNode("rookie", "Rookie", 0),
        ClubRankNode("veteran", "Veteran", 1200, Vector("priority-lineup")),
        ClubRankNode("captain", "Captain", 2400, Vector("approve-roster", "manage-bank"))
      ),
      actor = principalFor(app, owner.id),
      occurredAt = now.plusSeconds(180),
      note = Some("season update")
    ).getOrElse(fail("rank tree update failed"))
    assertEquals(updatedRankTree.rankTree.map(_.code), Vector("rookie", "veteran", "captain"))
    assertEquals(updatedRankTree.rankTree.last.privileges, Vector("approve-roster", "manage-bank"))

    val auditTypes = app.auditEventRepository.findByAggregate("club", club.id.value).map(_.eventType)
    assert(auditTypes.contains("ClubTreasuryAdjusted"))
    assert(auditTypes.contains("ClubPointPoolAdjusted"))
    assert(auditTypes.contains("ClubRankTreeUpdated"))
  }

  test("multi-round scheduling respects pool size and advances rounds incrementally") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T15:00:00Z")

    val players = (1 to 8).toVector.map { index =>
      app.playerService.registerPlayer(
        s"multi-round-$index",
        s"Player$index",
        RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        now,
        1600 - index
      )
    }

    val stage = TournamentStage(
      IdGenerator.stageId(),
      "Swiss Marathon",
      StageFormat.Swiss,
      order = 1,
      roundCount = 2,
      schedulingPoolSize = 1
    )
    val tournament = app.tournamentService.createTournament(
      "Scheduling Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id))
    app.tournamentService.publishTournament(tournament.id)

    val firstWave = app.tournamentService.scheduleStageTables(tournament.id, stage.id)
    assertEquals(firstWave.size, 1)
    assertEquals(firstWave.head.stageRoundNumber, 1)

    app.tableService.startTable(firstWave.head.id, now.plusSeconds(60))
    app.tableService.recordCompletedTable(
      firstWave.head.id,
      demoPaifuForResult(
        firstWave.head,
        tournament.id,
        stage.id,
        now.plusSeconds(120),
        winner = firstWave.head.seats.head.playerId,
        target = firstWave.head.seats(1).playerId
      )
    )

    val secondWave = app.tournamentService.scheduleStageTables(tournament.id, stage.id)
    assertEquals(secondWave.size, 2)
    assertEquals(secondWave.last.stageRoundNumber, 1)

    app.tableService.startTable(secondWave.last.id, now.plusSeconds(180))
    app.tableService.recordCompletedTable(
      secondWave.last.id,
      demoPaifuForResult(
        secondWave.last,
        tournament.id,
        stage.id,
        now.plusSeconds(240),
        winner = secondWave.last.seats.head.playerId,
        target = secondWave.last.seats(1).playerId
      )
    )

    val thirdWave = app.tournamentService.scheduleStageTables(tournament.id, stage.id)
    assertEquals(thirdWave.count(_.stageRoundNumber == 2), 1)

    val stageStateAfterAdvance = app.tournamentRepository.findById(tournament.id).flatMap(_.stages.find(_.id == stage.id))
      .getOrElse(fail("stage missing after advance"))
    assertEquals(stageStateAfterAdvance.currentRound, 2)
    assert(stageStateAfterAdvance.pendingTablePlans.nonEmpty)
  }


  test("reserve lineup seats backfill unavailable active players during scheduling") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T16:00:00Z")

    val owner = app.playerService.registerPlayer("reserve-owner", "Owner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val alpha = app.playerService.registerPlayer("reserve-alpha", "Alpha", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700)
    val bravo = app.playerService.registerPlayer("reserve-bravo", "Bravo", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val absent = app.playerService.registerPlayer("reserve-absent", "Absent", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val reserve = app.playerService.registerPlayer("reserve-bench", "Reserve", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1550)

    val club = app.clubService.createClub("Reserve Club", owner.id, now, owner.asPrincipal)
    Vector(alpha, bravo, absent, reserve).foreach(player =>
      app.clubService.addMember(club.id, player.id, principalFor(app, owner.id))
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Reserve Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Reserve Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    app.tournamentService.registerClub(tournament.id, club.id)
    app.tournamentService.publishTournament(tournament.id)
    app.tournamentService.submitLineup(
      tournament.id,
      stage.id,
      StageLineupSubmission(
        id = IdGenerator.lineupSubmissionId(),
        clubId = club.id,
        submittedBy = owner.id,
        submittedAt = now,
        seats = Vector(
          StageLineupSeat(owner.id),
          StageLineupSeat(alpha.id),
          StageLineupSeat(bravo.id),
          StageLineupSeat(absent.id),
          StageLineupSeat(reserve.id, reserve = true)
        )
      ),
      principalFor(app, owner.id)
    )

    app.playerRepository.save(absent.copy(status = PlayerStatus.Suspended))

    val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id).head
    assert(table.seats.exists(_.playerId == reserve.id))
    assert(!table.seats.exists(_.playerId == absent.id))
    assert(table.seats.forall(_.clubId.contains(club.id)))
  }

  test("swiss pairingMethod can switch from balanced elo to snake grouping") {
    val now = Instant.parse("2026-03-16T08:00:00Z")

    def scheduleWith(pairingMethod: String): (Vector[String], Vector[Set[String]]) =
      val app = ApplicationContext.inMemory()
      val players = (1 to 8).toVector.map { index =>
        app.playerService.registerPlayer(
          s"pairing-$pairingMethod-$index",
          s"Pairing${pairingMethod.capitalize}$index",
          RankSnapshot(RankPlatform.Tenhou, "4-dan"),
          now,
          2000 - index * 50
        )
      }

      val stage = TournamentStage(
        IdGenerator.stageId(),
        s"Swiss $pairingMethod",
        StageFormat.Swiss,
        order = 1,
        roundCount = 1,
        swissRule = Some(SwissRuleConfig(pairingMethod = pairingMethod))
      )
      val tournament = app.tournamentService.createTournament(
        s"Swiss ${pairingMethod} Cup",
        "QA",
        now,
        now.plusSeconds(7200),
        Vector(stage)
      )

      players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id))
      app.tournamentService.publishTournament(tournament.id)
      val groupedNicknames = app.tournamentService.scheduleStageTables(tournament.id, stage.id)
        .sortBy(_.tableNo)
        .map(table => table.seats.flatMap(seat => players.find(_.id == seat.playerId).map(_.nickname)).toSet)

      (players.map(_.nickname), groupedNicknames)

    val (balancedNames, balancedTables) = scheduleWith("balanced-elo")
    val (snakeNames, snakeTables) = scheduleWith("snake")

    assertEquals(
      balancedTables,
      Vector(
        balancedNames.take(4).toSet,
        balancedNames.drop(4).toSet
      )
    )
    assertEquals(
      snakeTables,
      Vector(
        Set(snakeNames(0), snakeNames(3), snakeNames(4), snakeNames(7)),
        Set(snakeNames(1), snakeNames(2), snakeNames(5), snakeNames(6))
      )
    )
  }

  test("swiss maxRounds limits scheduling horizon and completion requirements") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T17:00:00Z")

    val players = (1 to 8).toVector.map { index =>
      app.playerService.registerPlayer(
        s"max-rounds-$index",
        s"MaxRounds$index",
        RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        now,
        1650 - index
      )
    }

    val stage = TournamentStage(
      IdGenerator.stageId(),
      "Limited Swiss",
      StageFormat.Swiss,
      order = 1,
      roundCount = 3,
      swissRule = Some(SwissRuleConfig(maxRounds = Some(2)))
    )
    val tournament = app.tournamentService.createTournament(
      "Limited Swiss Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id))
    app.tournamentService.publishTournament(tournament.id)

    val firstRoundTables = app.tournamentService.scheduleStageTables(tournament.id, stage.id)
      .filter(_.stageRoundNumber == 1)
    assertEquals(firstRoundTables.size, 2)
    firstRoundTables.zipWithIndex.foreach { (table, index) =>
      app.tableService.startTable(table.id, now.plusSeconds(60L * (index + 1)))
      app.tableService.recordCompletedTable(
        table.id,
        demoPaifuForResult(
          table,
          tournament.id,
          stage.id,
          now.plusSeconds(180L * (index + 1)),
          winner = table.seats.head.playerId,
          target = table.seats(1).playerId
        )
      )
    }

    val secondRoundTables = app.tournamentService.scheduleStageTables(tournament.id, stage.id)
      .filter(_.stageRoundNumber == 2)
    assertEquals(secondRoundTables.size, 2)
    secondRoundTables.zipWithIndex.foreach { (table, index) =>
      app.tableService.startTable(table.id, now.plusSeconds(420L + 60L * index))
      app.tableService.recordCompletedTable(
        table.id,
        demoPaifuForResult(
          table,
          tournament.id,
          stage.id,
          now.plusSeconds(540L + 180L * index),
          winner = table.seats.head.playerId,
          target = table.seats(1).playerId
        )
      )
    }

    val afterLimit = app.tournamentService.scheduleStageTables(tournament.id, stage.id)
    assertEquals(afterLimit.count(_.stageRoundNumber == 3), 0)

    val stageState = app.tournamentRepository.findById(tournament.id).flatMap(_.stages.find(_.id == stage.id))
      .getOrElse(fail("stage missing after max-round scheduling"))
    assertEquals(stageState.currentRound, 2)
    assertEquals(stageState.pendingTablePlans.size, 0)

    val advancement = app.tournamentService.completeStage(
      tournament.id,
      stage.id,
      AccessPrincipal.system,
      now.plusSeconds(1200)
    )
    assert(advancement.nonEmpty)
    assertEquals(
      app.tournamentRepository.findById(tournament.id).flatMap(_.stages.find(_.id == stage.id)).map(_.status),
      Some(StageStatus.Completed)
    )
  }

  test("knockout seeding policy can follow rating instead of ranking order") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T09:00:00Z")

    val players = (1 to 8).toVector.map { index =>
      val player = Player(
        id = PlayerId(f"seed-$index%02d"),
        userId = s"seed-user-$index",
        nickname = s"SeedPlayer$index",
        registeredAt = now,
        currentRank = RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        elo = 1200 + index * 100
      )
      app.playerRepository.save(player)
    }

    def bracketForPolicy(policy: String): KnockoutBracketSnapshot =
      val stage = TournamentStage(
        id = IdGenerator.stageId(),
        name = s"Knockout $policy",
        format = StageFormat.Knockout,
        order = 1,
        roundCount = 1,
        advancementRule = AdvancementRule(AdvancementRuleType.KnockoutElimination, targetTableCount = Some(2)),
        knockoutRule = Some(KnockoutRuleConfig(bracketSize = Some(8), seedingPolicy = policy))
      )
      val tournament = app.tournamentService.createTournament(
        s"Knockout ${policy} Cup",
        "QA",
        now,
        now.plusSeconds(7200),
        Vector(stage)
      )
      players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id))
      app.tournamentService.publishTournament(tournament.id)
      app.tournamentService.stageKnockoutBracket(tournament.id, stage.id, now.plusSeconds(60))

    val rankingBracket = bracketForPolicy("ranking")
    val ratingBracket = bracketForPolicy("rating")

    val rankingFirstMatch = rankingBracket.rounds.head.matches.head
    val ratingFirstMatch = ratingBracket.rounds.head.matches.head

    assertEquals(rankingFirstMatch.slots.flatMap(_.playerId).headOption, Some(PlayerId("seed-01")))
    assertEquals(ratingFirstMatch.slots.flatMap(_.playerId).headOption, Some(PlayerId("seed-08")))
    assertNotEquals(rankingFirstMatch.slots.flatMap(_.playerId), ratingFirstMatch.slots.flatMap(_.playerId))
    assert(ratingBracket.summary.contains("rating"))
  }

  test("club relations stay reciprocal when updated or cleared") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T18:00:00Z")

    val ownerA = app.playerService.registerPlayer("relation-a", "RelationA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650)
    val ownerB = app.playerService.registerPlayer("relation-b", "RelationB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640)
    val clubA = app.clubService.createClub("Alliance A", ownerA.id, now, ownerA.asPrincipal)
    val clubB = app.clubService.createClub("Alliance B", ownerB.id, now, ownerB.asPrincipal)

    app.clubService.updateRelation(
      clubA.id,
      ClubRelation(clubB.id, ClubRelationKind.Alliance, now, Some("shared training")),
      principalFor(app, ownerA.id),
      now
    )

    val alliedA = app.clubRepository.findById(clubA.id).getOrElse(fail("clubA missing"))
    val alliedB = app.clubRepository.findById(clubB.id).getOrElse(fail("clubB missing"))
    assertEquals(alliedA.relations.map(_.targetClubId), Vector(clubB.id))
    assertEquals(alliedA.relations.map(_.relation), Vector(ClubRelationKind.Alliance))
    assertEquals(alliedB.relations.map(_.targetClubId), Vector(clubA.id))
    assertEquals(alliedB.relations.map(_.relation), Vector(ClubRelationKind.Alliance))

    app.clubService.updateRelation(
      clubA.id,
      ClubRelation(clubB.id, ClubRelationKind.Neutral, now.plusSeconds(60), Some("season reset")),
      principalFor(app, ownerA.id),
      now.plusSeconds(60)
    )

    assertEquals(app.clubRepository.findById(clubA.id).map(_.relations), Some(Vector.empty))
    assertEquals(app.clubRepository.findById(clubB.id).map(_.relations), Some(Vector.empty))
    val auditTypes = app.auditEventRepository.findByAggregate("club", clubA.id.value).map(_.eventType)
    assert(auditTypes.contains("ClubRelationUpdated"))
  }


  test("swiss standings can reset each round when carryOverPoints is disabled") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T19:00:00Z")

    val players = Vector(
      app.playerService.registerPlayer("carry-a", "CarryA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      app.playerService.registerPlayer("carry-b", "CarryB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640),
      app.playerService.registerPlayer("carry-c", "CarryC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1630),
      app.playerService.registerPlayer("carry-d", "CarryD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620)
    )

    val stage = TournamentStage(
      IdGenerator.stageId(),
      "Reset Swiss",
      StageFormat.Swiss,
      order = 1,
      roundCount = 2,
      swissRule = Some(SwissRuleConfig(carryOverPoints = false))
    )
    val tournament = app.tournamentService.createTournament(
      "Reset Carry Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id))
    app.tournamentService.publishTournament(tournament.id)

    val roundOne = app.tournamentService.scheduleStageTables(tournament.id, stage.id).head
    roundOne.seats.foreach(seat =>
      app.tableService.updateSeatState(
        roundOne.id,
        seat.seat,
        principalFor(app, seat.playerId),
        ready = Some(true)
      )
    )
    app.tableService.startTable(roundOne.id, now.plusSeconds(60))
    app.tableService.recordCompletedTable(
      roundOne.id,
      demoPaifuForResult(
        roundOne,
        tournament.id,
        stage.id,
        now.plusSeconds(120),
        winner = roundOne.seats(0).playerId,
        target = roundOne.seats(3).playerId
      )
    )

    val roundTwo = app.tournamentService.scheduleStageTables(tournament.id, stage.id)
      .find(_.stageRoundNumber == 2)
      .getOrElse(fail("round two table missing"))
    roundTwo.seats.foreach(seat =>
      app.tableService.updateSeatState(
        roundTwo.id,
        seat.seat,
        principalFor(app, seat.playerId),
        ready = Some(true)
      )
    )
    app.tableService.startTable(roundTwo.id, now.plusSeconds(180))
    app.tableService.recordCompletedTable(
      roundTwo.id,
      demoPaifuForResult(
        roundTwo,
        tournament.id,
        stage.id,
        now.plusSeconds(240),
        winner = roundTwo.seats(1).playerId,
        target = roundTwo.seats(0).playerId
      )
    )

    val standings = app.tournamentService.stageStandings(tournament.id, stage.id, now.plusSeconds(300))
    assertEquals(
      standings.entries.map(_.playerId).take(4),
      Vector(
        roundTwo.seats(1).playerId,
        roundTwo.seats(2).playerId,
        roundTwo.seats(3).playerId,
        roundTwo.seats(0).playerId
      )
    )
    assertEquals(
      standings.entries.find(_.playerId == roundTwo.seats(0).playerId).map(_.matchesPlayed),
      Some(1)
    )
  }

  test("table seat readiness and disconnect workflow gates table start") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T20:00:00Z")

    val players = Vector(
      app.playerService.registerPlayer("seat-a", "SeatA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      app.playerService.registerPlayer("seat-b", "SeatB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640),
      app.playerService.registerPlayer("seat-c", "SeatC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1630),
      app.playerService.registerPlayer("seat-d", "SeatD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Seat State Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Seat State Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id))
    app.tournamentService.publishTournament(tournament.id)

    val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id).head
    val disconnectedSeat = table.seats(0)
    app.tableService.updateSeatState(
      table.id,
      disconnectedSeat.seat,
      principalFor(app, disconnectedSeat.playerId),
      disconnected = Some(true),
      note = Some("network drop")
    )
    table.seats.tail.foreach(seat =>
      app.tableService.updateSeatState(
        table.id,
        seat.seat,
        principalFor(app, seat.playerId),
        ready = Some(true)
      )
    )
    intercept[IllegalArgumentException] {
      app.tableService.startTable(table.id, now.plusSeconds(120))
    }

    app.tableService.updateSeatState(
      table.id,
      disconnectedSeat.seat,
      principalFor(app, disconnectedSeat.playerId),
      disconnected = Some(false)
    )
    app.tableService.updateSeatState(
      table.id,
      disconnectedSeat.seat,
      principalFor(app, disconnectedSeat.playerId),
      ready = Some(true)
    )

    val started = app.tableService.startTable(table.id, now.plusSeconds(180))
      .getOrElse(fail("table did not start"))
    assertEquals(started.status, TableStatus.InProgress)
    assert(started.seats.forall(_.ready))
    assert(!started.seats.exists(_.disconnected))
  }


  test("club honors can be awarded and revoked with audit trail") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T14:30:00Z")

    val owner = app.playerService.registerPlayer("club-honor-owner", "HonorOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val club = app.clubService.createClub("Honor Club", owner.id, now, owner.asPrincipal)

    val afterAward = app.clubService.awardHonor(
      club.id,
      ClubHonor("Spring Split Champion", now.plusSeconds(60), Some("won grand finals")),
      principalFor(app, owner.id),
      now.plusSeconds(60)
    ).getOrElse(fail("honor award failed"))
    assertEquals(afterAward.honors.map(_.title), Vector("Spring Split Champion"))

    val afterUpdate = app.clubService.awardHonor(
      club.id,
      ClubHonor("Spring Split Champion", now.plusSeconds(120), Some("updated note")),
      principalFor(app, owner.id),
      now.plusSeconds(120)
    ).getOrElse(fail("honor update failed"))
    assertEquals(afterUpdate.honors.size, 1)
    assertEquals(afterUpdate.honors.head.note, Some("updated note"))

    val afterRevoke = app.clubService.revokeHonor(
      club.id,
      "Spring Split Champion",
      principalFor(app, owner.id),
      now.plusSeconds(180),
      Some("season rollover")
    ).getOrElse(fail("honor revoke failed"))
    assertEquals(afterRevoke.honors, Vector.empty)

    val auditTypes = app.auditEventRepository.findByAggregate("club", club.id.value).map(_.eventType)
    assert(auditTypes.contains("ClubHonorAwarded"))
    assert(auditTypes.contains("ClubHonorRevoked"))
  }

  test("round settlement validation and match record notes are populated from paifu") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T19:30:00Z")

    val players = Vector(
      app.playerService.registerPlayer("settle-a", "SettleA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      app.playerService.registerPlayer("settle-b", "SettleB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640),
      app.playerService.registerPlayer("settle-c", "SettleC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1630),
      app.playerService.registerPlayer("settle-d", "SettleD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620)
    )

    def prepareTable(label: String, offset: Long): (Tournament, TournamentStage, Table) =
      val stage = TournamentStage(IdGenerator.stageId(), s"$label Stage", StageFormat.Swiss, 1, 1)
      val tournament = app.tournamentService.createTournament(
        s"$label Cup",
        "QA",
        now.plusSeconds(offset),
        now.plusSeconds(offset + 7200),
        Vector(stage)
      )
      players.foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id))
      app.tournamentService.publishTournament(tournament.id)
      val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id).head
      app.tableService.startTable(table.id, now.plusSeconds(offset + 60))
      (tournament, stage, table)

    val (validTournament, validStage, validTable) = prepareTable("Settlement Valid", 0)
    val basePaifu = demoPaifuForResult(
      validTable,
      validTournament.id,
      validStage.id,
      now.plusSeconds(120),
      winner = validTable.seats.head.playerId,
      target = validTable.seats(1).playerId
    )
    val validPaifu = basePaifu.copy(
      rounds = Vector(
        basePaifu.rounds.head.copy(
          descriptor = KyokuDescriptor(SeatWind.East, 1, honba = 1),
          result = basePaifu.rounds.head.result.copy(
            settlement = Some(
              RoundSettlement(
                riichiSticksDelta = 1000,
                honbaPayment = 300,
                notes = Vector("riichi sticks awarded")
              )
            )
          )
        )
      )
    )

    app.tableService.recordCompletedTable(validTable.id, validPaifu)
    val record = app.matchRecordRepository.findByTable(validTable.id).getOrElse(fail("record missing"))
    assert(record.notes.exists(_.contains("settlement riichi=1000 honba=300")))

    val (invalidTournament, invalidStage, invalidTable) = prepareTable("Settlement Invalid", 8000)
    val invalidBasePaifu = demoPaifuForResult(
      invalidTable,
      invalidTournament.id,
      invalidStage.id,
      now.plusSeconds(8120),
      winner = invalidTable.seats.head.playerId,
      target = invalidTable.seats(1).playerId
    )
    val invalidPaifu = invalidBasePaifu.copy(
      rounds = Vector(
        invalidBasePaifu.rounds.head.copy(
          descriptor = KyokuDescriptor(SeatWind.East, 1, honba = 0),
          actions = invalidBasePaifu.rounds.head.actions.filterNot(_.actionType == PaifuActionType.Riichi),
          result = invalidBasePaifu.rounds.head.result.copy(
            settlement = Some(RoundSettlement(riichiSticksDelta = 1000, honbaPayment = 0))
          )
        )
      )
    )

    intercept[IllegalArgumentException] {
      app.tableService.recordCompletedTable(invalidTable.id, invalidPaifu)
    }
  }

private def principalFor(app: ApplicationContext, playerId: PlayerId): AccessPrincipal =
  app.playerRepository.findById(playerId).get.asPrincipal

private def demoPaifu(
    table: Table,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    recordedAt: Instant
): Paifu =
  val orderedSeats = table.seats.sortBy(_.seat.ordinal)
  demoPaifuForResult(
    table,
    tournamentId,
    stageId,
    recordedAt,
    winner = orderedSeats(1).playerId,
    target = orderedSeats(2).playerId
  )

private def demoPaifuForResult(
    table: Table,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    recordedAt: Instant,
    winner: PlayerId,
    target: PlayerId
): Paifu =
  val orderedSeats = table.seats.sortBy(_.seat.ordinal)
  val east = orderedSeats(0).playerId
  val south = orderedSeats(1).playerId
  val west = orderedSeats(2).playerId
  val north = orderedSeats(3).playerId
  val seatByPlayer = table.seats.map(seat => seat.playerId -> seat.seat).toMap
  val untouchedPlayers = orderedSeats.map(_.playerId).filterNot(playerId =>
    playerId == winner || playerId == target
  )
  val secondPlayer = untouchedPlayers.headOption.getOrElse(east)
  val thirdPlayer = untouchedPlayers.drop(1).headOption.getOrElse(north)
  val finalPoints = Map(
    winner -> 32700,
    secondPlayer -> 25000,
    thirdPlayer -> 25000,
    target -> 17300
  )
  val placements = Vector(winner, secondPlayer, thirdPlayer, target)

  Paifu(
    id = IdGenerator.paifuId(),
    metadata = PaifuMetadata(
      recordedAt = recordedAt,
      source = "test-fixture",
      tableId = table.id,
      tournamentId = tournamentId,
      stageId = stageId,
      seats = table.seats
    ),
    rounds = Vector(
      KyokuRecord(
        descriptor = KyokuDescriptor(SeatWind.East, 1, 0),
        initialHands = table.seats.map(seat => seat.playerId -> Vector("1m", "1p", "1s")).toMap,
        actions = Vector(
          PaifuAction(1, Some(east), PaifuActionType.Draw, Some("4m"), Some(3)),
          PaifuAction(2, Some(winner), PaifuActionType.Riichi, note = Some("riichi")),
          PaifuAction(3, Some(winner), PaifuActionType.Win, Some("7p"), Some(0))
        ),
        result = AgariResult(
          outcome = HandOutcome.Ron,
          winner = Some(winner),
          target = Some(target),
          han = Some(3),
          fu = Some(40),
          yaku = Vector(Yaku("Riichi", 1), Yaku("Pinfu", 1), Yaku("Ippatsu", 1)),
          points = 7700,
          scoreChanges = Vector(
            ScoreChange(east, if east == winner then 7700 else if east == target then -7700 else 0),
            ScoreChange(south, if south == winner then 7700 else if south == target then -7700 else 0),
            ScoreChange(west, if west == winner then 7700 else if west == target then -7700 else 0),
            ScoreChange(north, if north == winner then 7700 else if north == target then -7700 else 0)
          )
        )
      )
    ),
    finalStandings = placements.zipWithIndex.map { case (playerId, index) =>
      FinalStanding(
        playerId,
        seatByPlayer(playerId),
        finalPoints(playerId),
        index + 1
      )
    }
  )
