package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.application.ports.GlobalDictionaryRepository
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.microservices.publicquery.api.PublicQueryService

class RiichiNexusCompetitionSuite extends FunSuite with RiichiNexusSuiteSupport:
  test("global dictionary normalizes ranks in the public player leaderboard") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T12:30:00Z")

    dictionaryGovernance(app).upsertDictionary(
      key = "rank.normalization.tenhou.5-dan",
      value = "550",
      actor = AccessPrincipal.system,
      updatedAt = now
    )
    dictionaryGovernance(app).upsertDictionary(
      key = "rank.normalization.mahjongsoul.master",
      value = "600",
      actor = AccessPrincipal.system,
      updatedAt = now.plusSeconds(1)
    )
    dictionaryGovernance(app).upsertDictionary(
      key = "rank.normalization.mahjongsoul.starWeight",
      value = "10",
      actor = AccessPrincipal.system,
      updatedAt = now.plusSeconds(2)
    )

    val tenhou = playerService(app).registerPlayer("rank-dict-a", "RankA", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)
    val soul = playerService(app).registerPlayer("rank-dict-b", "RankB", RankSnapshot(RankPlatform.MahjongSoul, "Master", Some(2)), now, 1700)

    val leaderboard = publicQueryService(app).publicPlayerLeaderboard(10)
    val soulEntry = leaderboard.find(_.playerId == soul.id).getOrElse(fail("soul entry missing"))
    val tenhouEntry = leaderboard.find(_.playerId == tenhou.id).getOrElse(fail("tenhou entry missing"))

    assertEquals(soulEntry.normalizedRankScore, Some(620))
    assertEquals(tenhouEntry.normalizedRankScore, Some(550))
    assertEquals(leaderboard.head.playerId, soul.id)
  }

  test("public player leaderboard reads global dictionary once per request") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T12:35:00Z")

    dictionaryGovernance(app).upsertDictionary(
      key = "rank.normalization.tenhou.5-dan",
      value = "550",
      actor = AccessPrincipal.system,
      updatedAt = now
    )
    dictionaryGovernance(app).upsertDictionary(
      key = "rank.normalization.mahjongsoul.master",
      value = "600",
      actor = AccessPrincipal.system,
      updatedAt = now.plusSeconds(1)
    )
    dictionaryGovernance(app).upsertDictionary(
      key = "rank.normalization.mahjongsoul.starWeight",
      value = "10",
      actor = AccessPrincipal.system,
      updatedAt = now.plusSeconds(2)
    )

    playerService(app).registerPlayer("rank-cache-a", "RankCacheA", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)
    playerService(app).registerPlayer("rank-cache-b", "RankCacheB", RankSnapshot(RankPlatform.MahjongSoul, "Master", Some(2)), now, 1700)

    val countingDictionaryRepository = new CountingGlobalDictionaryRepository(globalDictionaryRepository(app))
    val publicQueryService = new PublicQueryService(
      tournamentRepository(app),
      tableRepository(app),
      playerRepository(app),
      clubRepository(app),
      countingDictionaryRepository
    )

    val leaderboard = publicQueryService.publicPlayerLeaderboard(10)

    assertEquals(leaderboard.size, 2)
    assertEquals(countingDictionaryRepository.findAllCalls, 1)
  }

  test("public player leaderboard records dictionary timing diagnostics") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T12:40:00Z")

    dictionaryGovernance(app).upsertDictionary(
      key = "rank.normalization.tenhou.5-dan",
      value = "550",
      actor = AccessPrincipal.system,
      updatedAt = now
    )
    playerService(app).registerPlayer("rank-diag-a", "RankDiagA", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)

    publicQueryService(app).publicPlayerLeaderboard(10)

    assertEquals(repositoryCallCount(app, "GlobalDictionaryRepository.findAll"), 1L)
    assert(repositoryTotalMillis(app, "GlobalDictionaryRepository.findAll") >= 0.0)
  }

  test("club power dictionary update repairs active clubs with one dictionary scan") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T12:45:00Z")

    val ownerA = playerService(app).registerPlayer("club-power-owner-a", "ClubPowerOwnerA", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val ownerB = playerService(app).registerPlayer("club-power-owner-b", "ClubPowerOwnerB", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    clubService(app).createClub("Club Power A", ownerA.id, now, ownerA.asPrincipal)
    clubService(app).createClub("Club Power B", ownerB.id, now.plusSeconds(1), ownerB.asPrincipal)

    val beforeCalls = repositoryCallCount(app, "GlobalDictionaryRepository.findAll")

    dictionaryGovernance(app).upsertDictionary(
      key = "club.power.eloWeight",
      value = "1.15",
      actor = AccessPrincipal.system,
      updatedAt = now.plusSeconds(2)
    )

    val afterCalls = repositoryCallCount(app, "GlobalDictionaryRepository.findAll")
    assertEquals(afterCalls - beforeCalls, 1L)
  }

  test("creating tournament with multiple templated stages reads global dictionary once") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T12:50:00Z")

    dictionaryGovernance(app).upsertDictionary(
      key = "tournament.rule-template.swiss-snake-template",
      value = "advancement=SwissCut;cutSize=8;pairingMethod=snake;maxRounds=2;schedulingPoolSize=2;note=template backed",
      actor = AccessPrincipal.system,
      updatedAt = now
    )

    val beforeCalls = repositoryCallCount(app, "GlobalDictionaryRepository.findAll")
    val stages = Vector(
      TournamentStage(
        id = IdGenerator.stageId(),
        name = "Template Stage A",
        format = StageFormat.Swiss,
        order = 1,
        roundCount = 2,
        advancementRule = AdvancementRule(AdvancementRuleType.Custom, templateKey = Some("swiss-snake-template"))
      ),
      TournamentStage(
        id = IdGenerator.stageId(),
        name = "Template Stage B",
        format = StageFormat.Swiss,
        order = 2,
        roundCount = 2,
        advancementRule = AdvancementRule(AdvancementRuleType.Custom, templateKey = Some("swiss-snake-template"))
      )
    )

    val tournament = tournamentService(app).createTournament(
      name = "Template Diagnostics Cup",
      organizer = "QA",
      startsAt = now.plusSeconds(60),
      endsAt = now.plusSeconds(7200),
      stages = stages
    )

    val afterCalls = repositoryCallCount(app, "GlobalDictionaryRepository.findAll")

    assertEquals(afterCalls - beforeCalls, 1L)
    assertEquals(tournament.stages.map(_.schedulingPoolSize), Vector(2, 2))
  }

  test("stage lineup preferred winds influence scheduled seats") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T13:00:00Z")

    val owner = playerService(app).registerPlayer("wind-owner", "Owner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val south = playerService(app).registerPlayer("wind-south", "South", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700)
    val west = playerService(app).registerPlayer("wind-west", "West", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val north = playerService(app).registerPlayer("wind-north", "North", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now)

    val club = clubService(app).createClub("Preferred Wind Club", owner.id, now, owner.asPrincipal)
    Vector(south, west, north).foreach(player =>
      clubService(app).addMember(club.id, player.id, principalFor(app, owner.id))
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Preferred Wind Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Preferred Wind Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    tournamentService(app).registerClub(tournament.id, club.id)
    tournamentService(app).publishTournament(tournament.id)
    tournamentService(app).submitLineup(
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

    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id).head
    val seatByPlayer = table.seats.map(seat => seat.playerId -> seat.seat).toMap

    assertEquals(seatByPlayer(owner.id), SeatWind.East)
    assertEquals(seatByPlayer(south.id), SeatWind.South)
    assertEquals(seatByPlayer(west.id), SeatWind.West)
    assertEquals(seatByPlayer(north.id), SeatWind.North)
  }

  test("multi-round scheduling respects pool size and advances rounds incrementally") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T15:00:00Z")

    val players = (1 to 8).toVector.map { index =>
      playerService(app).registerPlayer(
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
    val tournament = tournamentService(app).createTournament(
      "Scheduling Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id))
    tournamentService(app).publishTournament(tournament.id)

    val firstWave = tournamentService(app).scheduleStageTables(tournament.id, stage.id)
    assertEquals(firstWave.size, 1)
    assertEquals(firstWave.head.stageRoundNumber, 1)

    tableService(app).startTable(firstWave.head.id, now.plusSeconds(60))
    tableService(app).recordCompletedTable(
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

    val secondWave = tournamentService(app).scheduleStageTables(tournament.id, stage.id)
    assertEquals(secondWave.size, 2)
    assertEquals(secondWave.last.stageRoundNumber, 1)

    tableService(app).startTable(secondWave.last.id, now.plusSeconds(180))
    tableService(app).recordCompletedTable(
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

    val thirdWave = tournamentService(app).scheduleStageTables(tournament.id, stage.id)
    assertEquals(thirdWave.count(_.stageRoundNumber == 2), 1)

    val stageStateAfterAdvance = tournamentRepository(app).findById(tournament.id).flatMap(_.stages.find(_.id == stage.id))
      .getOrElse(fail("stage missing after advance"))
    assertEquals(stageStateAfterAdvance.currentRound, 2)
    assert(stageStateAfterAdvance.pendingTablePlans.nonEmpty)
  }


  test("reserve lineup seats backfill unavailable active players during scheduling") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T16:00:00Z")

    val owner = playerService(app).registerPlayer("reserve-owner", "Owner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val alpha = playerService(app).registerPlayer("reserve-alpha", "Alpha", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700)
    val bravo = playerService(app).registerPlayer("reserve-bravo", "Bravo", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val absent = playerService(app).registerPlayer("reserve-absent", "Absent", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now)
    val reserve = playerService(app).registerPlayer("reserve-bench", "Reserve", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1550)

    val club = clubService(app).createClub("Reserve Club", owner.id, now, owner.asPrincipal)
    Vector(alpha, bravo, absent, reserve).foreach(player =>
      clubService(app).addMember(club.id, player.id, principalFor(app, owner.id))
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Reserve Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Reserve Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    tournamentService(app).registerClub(tournament.id, club.id)
    tournamentService(app).publishTournament(tournament.id)
    tournamentService(app).submitLineup(
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

    val suspendedAbsent = playerRepository(app).findById(absent.id).getOrElse(fail("absent player missing"))
    playerRepository(app).save(suspendedAbsent.copy(status = PlayerStatus.Suspended))

    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id).head
    assert(table.seats.exists(_.playerId == reserve.id))
    assert(!table.seats.exists(_.playerId == absent.id))
    assert(table.seats.forall(_.clubId.contains(club.id)))
  }

  test("swiss pairingMethod can switch from balanced elo to snake grouping") {
    val now = Instant.parse("2026-03-16T08:00:00Z")

    def scheduleWith(pairingMethod: String): (Vector[String], Vector[Set[String]]) =
      val app = ApplicationContext.inMemory()
      val players = (1 to 8).toVector.map { index =>
        playerService(app).registerPlayer(
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
      val tournament = tournamentService(app).createTournament(
        s"Swiss $pairingMethod Cup",
        "QA",
        now,
        now.plusSeconds(7200),
        Vector(stage)
      )

      players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id))
      tournamentService(app).publishTournament(tournament.id)
      val groupedNicknames = tournamentService(app).scheduleStageTables(tournament.id, stage.id)
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

  test("round robin stages rotate dedicated table pods across rounds") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T10:00:00Z")

    val players = (1 to 8).toVector.map { index =>
      playerService(app).registerPlayer(
        s"rr-$index",
        s"RR$index",
        RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        now,
        2000 - index * 50
      )
    }

    val stage = TournamentStage(IdGenerator.stageId(), "Round Robin Stage", StageFormat.RoundRobin, 1, 2)
    val tournament = tournamentService(app).createTournament(
      "Round Robin Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id))
    tournamentService(app).publishTournament(tournament.id)

    val roundOneTables = tournamentService(app).scheduleStageTables(tournament.id, stage.id).sortBy(_.tableNo)
    val nicknameById = players.map(player => player.id -> player.nickname).toMap
    val roundOneGroups = roundOneTables.map(table => table.seats.map(seat => nicknameById(seat.playerId)).toSet)
    assertEquals(
      roundOneGroups,
      Vector(
        Set("RR1", "RR3", "RR5", "RR7"),
        Set("RR2", "RR4", "RR6", "RR8")
      )
    )

    roundOneTables.zipWithIndex.foreach { case (table, index) =>
      tableService(app).startTable(table.id, now.plusSeconds(60L * (index + 1)))
      tableService(app).recordCompletedTable(
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

    val roundTwoTables = tournamentService(app).scheduleStageTables(tournament.id, stage.id)
      .filter(_.stageRoundNumber == 2)
      .sortBy(_.tableNo)
    val roundTwoGroups = roundTwoTables.map(table => table.seats.map(seat => nicknameById(seat.playerId)).toSet)
    assertEquals(
      roundTwoGroups,
      Vector(
        Set("RR1", "RR4", "RR5", "RR8"),
        Set("RR2", "RR3", "RR6", "RR7")
      )
    )
  }

  test("custom stages honor targetTableCount for scheduling and completion") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T10:30:00Z")

    val players = (1 to 8).toVector.map { index =>
      playerService(app).registerPlayer(
        s"custom-$index",
        s"Custom$index",
        RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        now,
        2100 - index * 50
      )
    }

    val stage = TournamentStage(
      IdGenerator.stageId(),
      "Custom Featured Stage",
      StageFormat.Custom,
      1,
      1,
      advancementRule = AdvancementRule(AdvancementRuleType.Custom, targetTableCount = Some(1), note = Some("top=4"))
    )
    val tournament = tournamentService(app).createTournament(
      "Custom Featured Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id))
    tournamentService(app).publishTournament(tournament.id)

    val scheduledTables = tournamentService(app).scheduleStageTables(tournament.id, stage.id)
    assertEquals(scheduledTables.size, 1)
    assertEquals(scheduledTables.head.seats.map(_.playerId).toSet, players.take(4).map(_.id).toSet)

    tableService(app).startTable(scheduledTables.head.id, now.plusSeconds(60))
    tableService(app).recordCompletedTable(
      scheduledTables.head.id,
      demoPaifuForResult(
        scheduledTables.head,
        tournament.id,
        stage.id,
        now.plusSeconds(120),
        winner = scheduledTables.head.seats.head.playerId,
        target = scheduledTables.head.seats(1).playerId
      )
    )

    val advancement = tournamentService(app).completeStage(
      tournament.id,
      stage.id,
      AccessPrincipal.system,
      now.plusSeconds(300)
    )
    assert(advancement.nonEmpty)
    assertEquals(advancement.map(_.qualifiedPlayerIds.size), Some(4))
    assertEquals(
      tournamentRepository(app).findById(tournament.id).flatMap(_.stages.find(_.id == stage.id)).map(_.status),
      Some(StageStatus.Completed)
    )
  }

  test("swiss maxRounds limits scheduling horizon and completion requirements") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T17:00:00Z")

    val players = (1 to 8).toVector.map { index =>
      playerService(app).registerPlayer(
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
    val tournament = tournamentService(app).createTournament(
      "Limited Swiss Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id))
    tournamentService(app).publishTournament(tournament.id)

    val firstRoundTables = tournamentService(app).scheduleStageTables(tournament.id, stage.id)
      .filter(_.stageRoundNumber == 1)
    assertEquals(firstRoundTables.size, 2)
    firstRoundTables.zipWithIndex.foreach { (table, index) =>
      tableService(app).startTable(table.id, now.plusSeconds(60L * (index + 1)))
      tableService(app).recordCompletedTable(
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

    val secondRoundTables = tournamentService(app).scheduleStageTables(tournament.id, stage.id)
      .filter(_.stageRoundNumber == 2)
    assertEquals(secondRoundTables.size, 2)
    secondRoundTables.zipWithIndex.foreach { (table, index) =>
      tableService(app).startTable(table.id, now.plusSeconds(420L + 60L * index))
      tableService(app).recordCompletedTable(
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

    val afterLimit = tournamentService(app).scheduleStageTables(tournament.id, stage.id)
    assertEquals(afterLimit.count(_.stageRoundNumber == 3), 0)

    val stageState = tournamentRepository(app).findById(tournament.id).flatMap(_.stages.find(_.id == stage.id))
      .getOrElse(fail("stage missing after max-round scheduling"))
    assertEquals(stageState.currentRound, 2)
    assertEquals(stageState.pendingTablePlans.size, 0)

    val advancement = tournamentService(app).completeStage(
      tournament.id,
      stage.id,
      AccessPrincipal.system,
      now.plusSeconds(1200)
    )
    assert(advancement.nonEmpty)
    assertEquals(
      tournamentRepository(app).findById(tournament.id).flatMap(_.stages.find(_.id == stage.id)).map(_.status),
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
      playerRepository(app).save(player)
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
      val tournament = tournamentService(app).createTournament(
        s"Knockout $policy Cup",
        "QA",
        now,
        now.plusSeconds(7200),
        Vector(stage)
      )
      players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id))
      tournamentService(app).publishTournament(tournament.id)
      tournamentStageQueries(app).stageKnockoutBracket(tournament.id, stage.id, now.plusSeconds(60))

    val rankingBracket = bracketForPolicy("ranking")
    val ratingBracket = bracketForPolicy("rating")

    val rankingFirstMatch = rankingBracket.rounds.head.matches.head
    val ratingFirstMatch = ratingBracket.rounds.head.matches.head

    assertEquals(rankingFirstMatch.slots.flatMap(_.playerId).headOption, Some(PlayerId("seed-01")))
    assertEquals(ratingFirstMatch.slots.flatMap(_.playerId).headOption, Some(PlayerId("seed-08")))
    assertNotEquals(rankingFirstMatch.slots.flatMap(_.playerId), ratingFirstMatch.slots.flatMap(_.playerId))
    assert(ratingBracket.summary.contains("rating"))
  }

  test("swiss standings can reset each round when carryOverPoints is disabled") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T19:00:00Z")

    val players = Vector(
      playerService(app).registerPlayer("carry-a", "CarryA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      playerService(app).registerPlayer("carry-b", "CarryB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640),
      playerService(app).registerPlayer("carry-c", "CarryC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1630),
      playerService(app).registerPlayer("carry-d", "CarryD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620)
    )

    val stage = TournamentStage(
      IdGenerator.stageId(),
      "Reset Swiss",
      StageFormat.Swiss,
      order = 1,
      roundCount = 2,
      swissRule = Some(SwissRuleConfig(carryOverPoints = false))
    )
    val tournament = tournamentService(app).createTournament(
      "Reset Carry Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id))
    tournamentService(app).publishTournament(tournament.id)

    val roundOne = tournamentService(app).scheduleStageTables(tournament.id, stage.id).head
    roundOne.seats.foreach(seat =>
      tableService(app).updateSeatState(
        roundOne.id,
        seat.seat,
        principalFor(app, seat.playerId),
        ready = Some(true)
      )
    )
    tableService(app).startTable(roundOne.id, now.plusSeconds(60))
    tableService(app).recordCompletedTable(
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

    val roundTwo = tournamentService(app).scheduleStageTables(tournament.id, stage.id)
      .find(_.stageRoundNumber == 2)
      .getOrElse(fail("round two table missing"))
    roundTwo.seats.foreach(seat =>
      tableService(app).updateSeatState(
        roundTwo.id,
        seat.seat,
        principalFor(app, seat.playerId),
        ready = Some(true)
      )
    )
    tableService(app).startTable(roundTwo.id, now.plusSeconds(180))
    tableService(app).recordCompletedTable(
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

    val standings = tournamentStageQueries(app).stageStandings(tournament.id, stage.id, now.plusSeconds(300))
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
      playerService(app).registerPlayer("seat-a", "SeatA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      playerService(app).registerPlayer("seat-b", "SeatB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640),
      playerService(app).registerPlayer("seat-c", "SeatC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1630),
      playerService(app).registerPlayer("seat-d", "SeatD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Seat State Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Seat State Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id))
    tournamentService(app).publishTournament(tournament.id)

    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id).head
    val disconnectedSeat = table.seats(0)
    tableService(app).updateSeatState(
      table.id,
      disconnectedSeat.seat,
      principalFor(app, disconnectedSeat.playerId),
      disconnected = Some(true),
      note = Some("network drop")
    )
    table.seats.tail.foreach(seat =>
      tableService(app).updateSeatState(
        table.id,
        seat.seat,
        principalFor(app, seat.playerId),
        ready = Some(true)
      )
    )
    intercept[IllegalArgumentException] {
      tableService(app).startTable(table.id, now.plusSeconds(120))
    }

    tableService(app).updateSeatState(
      table.id,
      disconnectedSeat.seat,
      principalFor(app, disconnectedSeat.playerId),
      disconnected = Some(false)
    )
    tableService(app).updateSeatState(
      table.id,
      disconnectedSeat.seat,
      principalFor(app, disconnectedSeat.playerId),
      ready = Some(true)
    )

    val started = tableService(app).startTable(table.id, now.plusSeconds(180))
      .getOrElse(fail("table did not start"))
    assertEquals(started.status, TableStatus.InProgress)
    assert(started.seats.forall(_.ready))
    assert(!started.seats.exists(_.disconnected))
  }

  test("round settlement validation and match record notes are populated from paifu") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T19:30:00Z")

    val players = Vector(
      playerService(app).registerPlayer("settle-a", "SettleA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      playerService(app).registerPlayer("settle-b", "SettleB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640),
      playerService(app).registerPlayer("settle-c", "SettleC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1630),
      playerService(app).registerPlayer("settle-d", "SettleD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620)
    )

    def prepareTable(label: String, offset: Long): (Tournament, TournamentStage, Table) =
      val stage = TournamentStage(IdGenerator.stageId(), s"$label Stage", StageFormat.Swiss, 1, 1)
      val tournament = tournamentService(app).createTournament(
        s"$label Cup",
        "QA",
        now.plusSeconds(offset),
        now.plusSeconds(offset + 7200),
        Vector(stage)
      )
      players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id))
      tournamentService(app).publishTournament(tournament.id)
      val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id).head
      tableService(app).startTable(table.id, now.plusSeconds(offset + 60))
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

    tableService(app).recordCompletedTable(validTable.id, validPaifu)
    val record = matchRecordRepository(app).findByTable(validTable.id).getOrElse(fail("record missing"))
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
          descriptor = KyokuDescriptor(SeatWind.East, 1),
          actions = invalidBasePaifu.rounds.head.actions.filterNot(_.actionType == PaifuActionType.Riichi),
          result = invalidBasePaifu.rounds.head.result.copy(
            settlement = Some(RoundSettlement(riichiSticksDelta = 1000, honbaPayment = 0))
          )
        )
      )
    )

    intercept[IllegalArgumentException] {
      tableService(app).recordCompletedTable(invalidTable.id, invalidPaifu)
    }
  }

