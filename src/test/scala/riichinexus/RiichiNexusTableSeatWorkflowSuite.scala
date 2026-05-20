package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.application.ports.GlobalDictionaryRepository
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*

class RiichiNexusTableSeatWorkflowSuite extends FunSuite with RiichiNexusSuiteSupport:

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
