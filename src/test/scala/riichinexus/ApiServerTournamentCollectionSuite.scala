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
import riichinexus.microservices.club.api.responses.*
import riichinexus.microservices.club.api.responses.ClubTournamentResponses.given
import riichinexus.microservices.club.api.requests.*
import riichinexus.microservices.dictionary.api.requests.UpsertDictionaryRequest
import riichinexus.microservices.opsanalytics.api.PerformanceDiagnosticsSnapshot
import riichinexus.microservices.shared.api.requests.OperatorRequest
import riichinexus.microservices.shared.api.requests.OperatorRequest.given
import riichinexus.microservices.publicquery.api.responses.*
import riichinexus.microservices.publicquery.api.responses.PublicQueryResponses.given
import riichinexus.microservices.tournament.api.responses.*
import riichinexus.microservices.tournament.api.responses.TournamentOperationResponses.given
import riichinexus.microservices.tournament.api.requests.SettlementRequests.given
import riichinexus.microservices.tournament.api.requests.StageRequests.given
import riichinexus.microservices.tournament.api.requests.TableRequests.given
import riichinexus.microservices.tournament.api.requests.*
import upickle.default.*

class ApiServerTournamentCollectionSuite extends FunSuite with ApiServerSuiteSupport:
  test("tables records and appeals endpoints support filtering and pagination") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:45:00Z")

    val admin = playerService(app).registerPlayer("filter-admin", "FilterAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    val players = (1 to 8).toVector.map { index =>
      if index == 1 then admin
      else playerService(app).registerPlayer(
        s"filter-p$index",
        s"Player$index",
        RankSnapshot(RankPlatform.Tenhou, "4-dan"),
        now,
        1500 + index
      )
    }
    val stage = TournamentStage(IdGenerator.stageId(), "Filter Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Filter Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player =>
      tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id))
    )
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    val tables = tournamentService(app).scheduleStageTables(
      tournament.id,
      stage.id,
      principalFor(app, admin.id)
    )

    val firstTable = tables.head
    val secondTable = tables.last
    tableService(app).startTable(firstTable.id, now.plusSeconds(60), principalFor(app, admin.id))
    val winner = firstTable.seats.head.playerId
    val target = firstTable.seats(1).playerId
    tableService(app).recordCompletedTable(
      firstTable.id,
      demoPaifuForResult(firstTable, tournament.id, stage.id, now.plusSeconds(120), winner, target),
      principalFor(app, admin.id)
    )

    appealService(app).fileAppeal(
      tableId = secondTable.id,
      openedBy = secondTable.seats.head.playerId,
      description = "waiting for adjudication",
      actor = principalFor(app, secondTable.seats.head.playerId),
      createdAt = now.plusSeconds(180)
    )

    withServer(app) { baseUrl =>
      val tablesResponse = get(
        s"$baseUrl/tables?tournamentId=${tournament.id.value}&status=AppealInProgress&limit=5"
      )
      assertEquals(tablesResponse.statusCode(), 200)
      val tablesPage = readPage[Table](tablesResponse.body())
      assertEquals(tablesPage.total, 1)
      assertEquals(tablesPage.items.map(_.id), Vector(secondTable.id))

      val recordsResponse = get(
        s"$baseUrl/records?tournamentId=${tournament.id.value}&playerId=${winner.value}&limit=1"
      )
      assertEquals(recordsResponse.statusCode(), 200)
      val recordsPage = readPage[MatchRecord](recordsResponse.body())
      assertEquals(recordsPage.total, 1)
      assertEquals(recordsPage.items.head.tableId, firstTable.id)

      val appealsResponse = get(
        s"$baseUrl/appeals?tournamentId=${tournament.id.value}&status=Open&limit=1"
      )
      assertEquals(appealsResponse.statusCode(), 200)
      val appealsPage = readPage[AppealTicket](appealsResponse.body())
      assertEquals(appealsPage.total, 1)
      assertEquals(appealsPage.items.head.tableId, secondTable.id)
      assertEquals(appealsPage.appliedFilters("status"), "Open")
    }
  }
