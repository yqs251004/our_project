package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.application.ports.GlobalDictionaryRepository
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.microservices.publicquery.api.PublicQueryService

class RiichiNexusStageRoundSchedulingSuite extends FunSuite with RiichiNexusSuiteSupport:

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
