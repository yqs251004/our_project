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

class ApiServerTournamentAdminSuite extends FunSuite with ApiServerSuiteSupport:
  test("tournament admin revoke endpoint removes scoped admin role") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T14:00:00Z")

    val root = playerService(app).registerPlayer("tour-root", "TournamentRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val adminA = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    val adminB = playerService(app).registerPlayer("tour-admin-b", "TournamentB", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    val stage = TournamentStage(IdGenerator.stageId(), "Admin Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Admin Cup",
      "QA",
      now,
      now.plusSeconds(3600),
      Vector(stage),
      adminId = Some(adminA.id)
    )
    tournamentService(app).assignTournamentAdmin(
      tournament.id,
      adminB.id,
      principalFor(app, adminA.id)
    )

    withServer(app) { baseUrl =>
      val revokeResponse = postApi(
        baseUrl,
        TournamentRevokeAdminAPIMessage(tournament.id.value, adminB.id.value, Some(adminA.id.value))
      )
      assertEquals(revokeResponse.statusCode(), 200)

      val updatedTournament = read[TournamentSummaryView](revokeResponse.body())
      assertEquals(updatedTournament.adminIds, Vector(adminA.id))
      val updatedAdmin = playerRepository(app).findById(adminB.id).getOrElse(fail("missing adminB"))
      assert(!updatedAdmin.roleGrants.exists(grant =>
        grant.role == RoleKind.TournamentAdmin && grant.tournamentId.contains(tournament.id)
      ))
    }
  }
