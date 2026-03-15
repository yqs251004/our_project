package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*

class RiichiNexusSuite extends FunSuite:
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
