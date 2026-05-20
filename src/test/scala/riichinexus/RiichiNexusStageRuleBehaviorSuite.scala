package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.application.ports.GlobalDictionaryRepository
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*

class RiichiNexusStageRuleBehaviorSuite extends FunSuite with RiichiNexusSuiteSupport:

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
