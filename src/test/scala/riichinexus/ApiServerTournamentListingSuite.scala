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
import riichinexus.microservices.publicquery.api.ListPublicSchedulesAPIMessage
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

class ApiServerTournamentListingSuite extends FunSuite with ApiServerSuiteSupport:
  test("tournament list and public schedules endpoints support filtered summaries") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:40:00Z")

    val admin = playerService(app).registerPlayer("schedule-admin", "ScheduleAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1810)
    val otherAdmin = playerService(app).registerPlayer("schedule-other-admin", "ScheduleOtherAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1790)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("schedule-b", "ScheduleB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      playerService(app).registerPlayer("schedule-c", "ScheduleC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620),
      playerService(app).registerPlayer("schedule-d", "ScheduleD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1590)
    )

    val publishedStage = TournamentStage(IdGenerator.stageId(), "Published Stage", StageFormat.Swiss, 1, 1)
    val publishedTournament = tournamentService(app).createTournament(
      "Schedule Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(publishedStage),
      adminId = Some(admin.id)
    )
    players.foreach(player =>
      tournamentService(app).registerPlayer(publishedTournament.id, player.id, principalFor(app, admin.id))
    )
    tournamentService(app).publishTournament(publishedTournament.id, principalFor(app, admin.id))
    tournamentService(app).scheduleStageTables(publishedTournament.id, publishedStage.id, principalFor(app, admin.id))

    tournamentService(app).createTournament(
      "Draft Cup",
      "QA",
      now.plusSeconds(3600),
      now.plusSeconds(10800),
      Vector(TournamentStage(IdGenerator.stageId(), "Draft Stage", StageFormat.Swiss, 1, 1)),
      adminId = Some(admin.id)
    )
    tournamentService(app).createTournament(
      "Other Organizer Cup",
      "Campus",
      now.plusSeconds(1800),
      now.plusSeconds(9000),
      Vector(TournamentStage(IdGenerator.stageId(), "Other Stage", StageFormat.Swiss, 1, 1)),
      adminId = Some(otherAdmin.id)
    )

    withServer(app) { baseUrl =>
      val tournamentsResponse = postApi(
        baseUrl,
        TournamentListAPIMessage(adminId = Some(admin.id.value), status = Some("InProgress"), organizer = Some("QA"))
      )
      assertEquals(tournamentsResponse.statusCode(), 200)
      val tournamentsPage = readPage[TournamentSummaryView](tournamentsResponse.body())
      assertEquals(tournamentsPage.total, 1)
      assertEquals(tournamentsPage.items.map(_.tournamentId), Vector(publishedTournament.id))

      val schedulesResponse = postJson(
        s"$baseUrl/api/listpublicschedulesapi",
        write(ListPublicSchedulesAPIMessage(tournamentStatus = Some("InProgress")))
      )
      assertEquals(schedulesResponse.statusCode(), 200)
      val schedulesPage = readPage[PublicScheduleView](schedulesResponse.body())
      assertEquals(schedulesPage.total, 1)
      assertEquals(schedulesPage.items.head.tournamentId, publishedTournament.id)
      assertEquals(schedulesPage.items.head.tableCount, 1)
      assertEquals(schedulesPage.items.head.participantCount, 4)
    }
  }
