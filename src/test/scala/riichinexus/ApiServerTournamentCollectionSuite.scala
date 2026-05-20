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
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest.given
import riichinexus.system.objects.PagedResponse
import riichinexus.microservices.publicquery.objects.apiTypes.*
import riichinexus.microservices.publicquery.objects.apiTypes.PublicQueryResponses.given
import riichinexus.microservices.tournament.api.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.TournamentOperationResponses.given
import riichinexus.microservices.tournament.appeal.api.*
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import riichinexus.microservices.tournament.appeal.objects.apiTypes.TournamentAppealResponses.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.*
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
      val tablesResponse = postApi(
        baseUrl,
        TournamentTableListAPIMessage(tournamentId = Some(tournament.id.value), status = Some("AppealInProgress"), limit = Some(5))
      )
      assertEquals(tablesResponse.statusCode(), 200)
      val tablesPage = readPage[TournamentTableView](tablesResponse.body())
      assertEquals(tablesPage.total, 1)
      assertEquals(tablesPage.items.map(_.tableId), Vector(secondTable.id))

      val recordsResponse = postApi(
        baseUrl,
        TournamentRecordListAPIMessage(tournamentId = Some(tournament.id.value), playerId = Some(winner.value), limit = Some(1))
      )
      assertEquals(recordsResponse.statusCode(), 200)
      val recordsPage = readPage[TournamentMatchRecordView](recordsResponse.body())
      assertEquals(recordsPage.total, 1)
      assertEquals(recordsPage.items.head.tableId, firstTable.id)

      val appealsResponse = postJson(
        s"$baseUrl/api/appeallistapi",
        write(AppealListAPIMessage(tournamentId = Some(tournament.id.value), status = Some("Open"), limit = Some(1)))
      )
      assertEquals(appealsResponse.statusCode(), 200)
      val appealsPage = read[PagedResponse[AppealTicketView]](appealsResponse.body())
      assertEquals(appealsPage.total, 1)
      assertEquals(appealsPage.items.head.tableId, secondTable.id)
      assertEquals(appealsPage.appliedFilters("status"), "Open")
    }
  }
