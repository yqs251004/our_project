package riichinexus

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

import munit.FunSuite



import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.objects.apiTypes.ClubTournamentResponses.given
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.UpsertDictionaryRequest
import riichinexus.microservices.opsanalytics.objects.apiTypes.PerformanceDiagnosticsSnapshot
import riichinexus.system.objects.apiTypes.OperatorRequest
import riichinexus.system.objects.apiTypes.OperatorRequest.given
import riichinexus.microservices.publicquery.objects.apiTypes.*
import riichinexus.microservices.publicquery.objects.apiTypes.PublicQueryResponses.given
import riichinexus.microservices.tournament.api.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.TournamentOperationResponses.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import upickle.default.*

class ApiServerTableSeatApiSuite extends FunSuite with ApiServerSuiteSupport:
  test("table seat state endpoint controls readiness and disconnect flow") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T16:30:00Z")

    val admin = playerService(app).registerPlayer("seat-api-admin", "SeatApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("seat-api-b", "SeatApiB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640),
      playerService(app).registerPlayer("seat-api-c", "SeatApiC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1630),
      playerService(app).registerPlayer("seat-api-d", "SeatApiD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620)
    )
    val stage = TournamentStage(IdGenerator.stageId(), "Seat API Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Seat API Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head

    withServer(app) { baseUrl =>
      val disconnectedSeat = table.seats(0)
      val disconnectResponse = postApi(
        baseUrl,
        TournamentTableUpdateSeatStateAPIMessage(
          table.id.value,
          disconnectedSeat.seat.toString,
          UpdateTableSeatStateRequest(disconnectedSeat.playerId.value, disconnected = Some(true), note = Some("wifi down"))
        )
      )
      assertEquals(disconnectResponse.statusCode(), 200)
      assert(read[TournamentTableView](disconnectResponse.body()).seats.find(_.seat == disconnectedSeat.seat).exists(_.disconnected))

      table.seats.tail.foreach { seat =>
        val readyResponse = postApi(
          baseUrl,
          TournamentTableUpdateSeatStateAPIMessage(
            table.id.value,
            seat.seat.toString,
            UpdateTableSeatStateRequest(seat.playerId.value, ready = Some(true))
          )
        )
        assertEquals(readyResponse.statusCode(), 200)
      }

      val stillBlocked = postApi(
        baseUrl,
        TournamentTableStartAPIMessage(table.id.value, Some(admin.id.value))
      )
      assertEquals(stillBlocked.statusCode(), 400)

      val reconnectResponse = postApi(
        baseUrl,
        TournamentTableUpdateSeatStateAPIMessage(
          table.id.value,
          disconnectedSeat.seat.toString,
          UpdateTableSeatStateRequest(disconnectedSeat.playerId.value, disconnected = Some(false))
        )
      )
      assertEquals(reconnectResponse.statusCode(), 200)
      val finalReady = postApi(
        baseUrl,
        TournamentTableUpdateSeatStateAPIMessage(
          table.id.value,
          disconnectedSeat.seat.toString,
          UpdateTableSeatStateRequest(disconnectedSeat.playerId.value, ready = Some(true))
        )
      )
      assertEquals(finalReady.statusCode(), 200)

      val started = postApi(
        baseUrl,
        TournamentTableStartAPIMessage(table.id.value, Some(admin.id.value))
      )
      assertEquals(started.statusCode(), 200)
      val startedTable = read[TournamentTableView](started.body())
      assertEquals(startedTable.status, TableStatus.InProgress)
      assert(startedTable.seats.forall(_.ready))
      assert(!startedTable.seats.exists(_.disconnected))
    }
  }

  test("player self-ready endpoint updates only their own seat before start") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T16:40:00Z")

    val admin = playerService(app).registerPlayer("self-ready-admin", "SelfReadyAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1760)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("self-ready-b", "SelfReadyB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      playerService(app).registerPlayer("self-ready-c", "SelfReadyC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640),
      playerService(app).registerPlayer("self-ready-d", "SelfReadyD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1630)
    )
    val outsider = playerService(app).registerPlayer("self-ready-x", "SelfReadyX", RankSnapshot(RankPlatform.Tenhou, "3-dan"), now, 1500)
    val stage = TournamentStage(IdGenerator.stageId(), "Self Ready Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Self Ready Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    val actorSeat = table.seats(1)

    withServer(app) { baseUrl =>
      val readyResponse = postApi(
        baseUrl,
        TournamentTableUpdateOwnReadyAPIMessage(table.id.value, UpdateOwnTableReadyStateRequest(actorSeat.playerId.value))
      )
      assertEquals(readyResponse.statusCode(), 200)
      val updatedTable = read[TournamentTableView](readyResponse.body())
      assert(updatedTable.seats.find(_.seat == actorSeat.seat).exists(_.ready))
      assertEquals(updatedTable.seats.count(_.ready), 1)

      val outsiderResponse = postApi(
        baseUrl,
        TournamentTableUpdateOwnReadyAPIMessage(table.id.value, UpdateOwnTableReadyStateRequest(outsider.id.value))
      )
      assertEquals(outsiderResponse.statusCode(), 400)
    }
  }
