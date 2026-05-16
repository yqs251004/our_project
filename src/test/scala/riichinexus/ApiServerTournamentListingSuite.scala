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
      val tournamentsResponse = get(
        s"$baseUrl/tournaments?adminId=${admin.id.value}&status=InProgress&organizer=QA"
      )
      assertEquals(tournamentsResponse.statusCode(), 200)
      val tournamentsPage = readPage[Tournament](tournamentsResponse.body())
      assertEquals(tournamentsPage.total, 1)
      assertEquals(tournamentsPage.items.map(_.id), Vector(publishedTournament.id))

      val schedulesResponse = get(
        s"$baseUrl/public/schedules?tournamentStatus=InProgress"
      )
      assertEquals(schedulesResponse.statusCode(), 200)
      val schedulesPage = readPage[PublicScheduleView](schedulesResponse.body())
      assertEquals(schedulesPage.total, 1)
      assertEquals(schedulesPage.items.head.tournamentId, publishedTournament.id)
      assertEquals(schedulesPage.items.head.tableCount, 1)
      assertEquals(schedulesPage.items.head.participantCount, 4)
    }
  }
