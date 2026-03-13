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

private def demoPaifu(
    table: Table,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    recordedAt: Instant
): Paifu =
  val orderedSeats = table.seats.sortBy(_.seat.ordinal)
  val east = orderedSeats(0).playerId
  val south = orderedSeats(1).playerId
  val west = orderedSeats(2).playerId
  val north = orderedSeats(3).playerId

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
          PaifuAction(2, Some(south), PaifuActionType.Riichi, note = Some("riichi")),
          PaifuAction(3, Some(south), PaifuActionType.Win, Some("7p"), Some(0))
        ),
        result = AgariResult(
          outcome = HandOutcome.Ron,
          winner = Some(south),
          target = Some(west),
          han = Some(3),
          fu = Some(40),
          yaku = Vector(Yaku("Riichi", 1), Yaku("Pinfu", 1), Yaku("Ippatsu", 1)),
          points = 7700,
          scoreChanges = Vector(
            ScoreChange(east, 0),
            ScoreChange(south, 7700),
            ScoreChange(west, -7700),
            ScoreChange(north, 0)
          )
        )
      )
    ),
    finalStandings = Vector(
      FinalStanding(south, SeatWind.South, 33400, 1),
      FinalStanding(east, SeatWind.East, 27100, 2),
      FinalStanding(north, SeatWind.North, 22400, 3),
      FinalStanding(west, SeatWind.West, 17100, 4)
    )
  )
