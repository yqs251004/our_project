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
import riichinexus.microservices.auth.api.requests.CreateGuestSessionRequest
import riichinexus.microservices.club.api.requests.*
import riichinexus.microservices.club.api.responses.*
import riichinexus.microservices.club.api.responses.ClubTournamentResponses.given
import riichinexus.microservices.dictionary.api.requests.*
import riichinexus.microservices.dictionary.api.responses.*
import riichinexus.microservices.dictionary.api.responses.DictionaryResponses.given
import riichinexus.microservices.opsanalytics.api.PerformanceDiagnosticsSnapshot
import riichinexus.microservices.opsanalytics.api.requests.{ProcessAdvancedStatsTasksRequest, RecomputeAdvancedStatsRequest}
import riichinexus.microservices.tournament.appeal.api.requests.*
import upickle.default.*

class ApiServerAdminDiagnosticsSuite extends FunSuite with ApiServerSuiteSupport:

  test("admin performance summary exposes normalized request and repository metrics") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:15:00Z")

    val root = playerService(app).registerPlayer("perf-root", "PerfRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val admin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    val owner = playerService(app).registerPlayer("perf-owner", "PerfOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650)
    val member = playerService(app).registerPlayer("perf-member", "PerfMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val club = clubService(app).createClub("Perf Club", owner.id, now, owner.asPrincipal)
    clubService(app).addMember(club.id, member.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val playersResponse = get(s"$baseUrl/players?clubId=${club.id.value}")
      assertEquals(playersResponse.statusCode(), 200)

      val clubDetailResponse = get(s"$baseUrl/public/clubs/${club.id.value}")
      assertEquals(clubDetailResponse.statusCode(), 200)

      val leaderboardResponse = get(s"$baseUrl/public/leaderboards/players")
      assertEquals(leaderboardResponse.statusCode(), 200)

      val summaryResponse = get(
        s"$baseUrl/admin/performance/summary?operatorId=${admin.id.value}&limit=20"
      )
      assertEquals(summaryResponse.statusCode(), 200)

      val summary = read[PerformanceDiagnosticsSnapshot](summaryResponse.body())
      assert(summary.totalRequestCount >= 2)
      assert(summary.totalRepositoryCallCount > 0)
      assert(summary.busiestRequests.exists(_.key == "GET /players"))
      assert(summary.busiestRequests.exists(_.key == "GET /public/clubs/:clubId"))
      assert(summary.slowestRequests.forall(!_.key.contains(club.id.value)))
      assert(summary.busiestRepositoryCalls.exists(entry =>
        entry.key.startsWith("PlayerRepository.") ||
          entry.key.startsWith("ClubRepository.") ||
          entry.key.startsWith("GlobalDictionaryRepository.") ||
          entry.key.startsWith("TournamentRepository.") ||
          entry.key.startsWith("MatchRecordRepository.")
      ))
      assert(summary.busiestRepositoryCalls.exists(_.key == "GlobalDictionaryRepository.findAll"))
    }
  }
