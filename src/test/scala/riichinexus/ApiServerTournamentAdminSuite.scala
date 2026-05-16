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
      val revokeResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/admins/${adminB.id.value}/revoke",
        write(OperatorRequest(Some(adminA.id.value)))
      )
      assertEquals(revokeResponse.statusCode(), 200)

      val updatedTournament = read[Tournament](revokeResponse.body())
      assertEquals(updatedTournament.admins, Vector(adminA.id))
      val updatedAdmin = playerRepository(app).findById(adminB.id).getOrElse(fail("missing adminB"))
      assert(!updatedAdmin.roleGrants.exists(grant =>
        grant.role == RoleKind.TournamentAdmin && grant.tournamentId.contains(tournament.id)
      ))
    }
  }
