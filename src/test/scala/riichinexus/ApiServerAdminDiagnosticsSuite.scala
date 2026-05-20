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
import riichinexus.microservices.player.api.ListPlayersAPIMessage
import riichinexus.microservices.publicquery.api.{GetPublicClubAPIMessage, PublicPlayerLeaderboardAPIMessage}
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.objects.apiTypes.ClubTournamentResponses.given
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.DictionaryResponses.given
import riichinexus.microservices.opsanalytics.api.OpsAnalyticsPerformanceSummaryAPIMessage
import riichinexus.microservices.opsanalytics.objects.apiTypes.PerformanceDiagnosticsSnapshot
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

class ApiServerAdminDiagnosticsSuite extends FunSuite with ApiServerSuiteSupport:

  test("admin performance summary exposes normalized request and repository metrics") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:15:00Z")

    val root = playerService(app).registerPlayer("perf-root", "PerfRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val admin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    val owner = playerService(app).registerPlayer("perf-owner", "PerfOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650)
    val member = playerService(app).registerPlayer("perf-member", "PerfMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val club = clubApi(app).createClub("Perf Club", owner.id, now, owner.asPrincipal)
    clubApi(app).addMember(club.id, member.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val playersResponse = postJson(
        s"$baseUrl/api/listplayersapi",
        write(ListPlayersAPIMessage(clubId = Some(club.id.value)))
      )
      assertEquals(playersResponse.statusCode(), 200)

      val clubDetailResponse = postJson(
        s"$baseUrl/api/getpublicclubapi",
        write(GetPublicClubAPIMessage(club.id.value))
      )
      assertEquals(clubDetailResponse.statusCode(), 200)

      val leaderboardResponse = postJson(
        s"$baseUrl/api/publicplayerleaderboardapi",
        write(PublicPlayerLeaderboardAPIMessage())
      )
      assertEquals(leaderboardResponse.statusCode(), 200)

      val summaryResponse = postJson(
        s"$baseUrl/api/opsanalyticsperformancesummaryapi",
        write(OpsAnalyticsPerformanceSummaryAPIMessage(admin.id, limit = Some(20)))
      )
      assertEquals(summaryResponse.statusCode(), 200)

      val summary = read[PerformanceDiagnosticsSnapshot](summaryResponse.body())
      assert(summary.totalRequestCount >= 2)
      assert(summary.totalRepositoryCallCount > 0)
      assert(summary.busiestRequests.exists(_.key == "POST /api/listplayersapi"))
      assert(summary.busiestRequests.exists(_.key == "POST /api/getpublicclubapi"))
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
