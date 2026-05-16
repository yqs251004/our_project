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

class ApiServerDictionaryNamespaceReviewSuite extends FunSuite with ApiServerSuiteSupport:
  test("dictionary namespace transfer rejects suspended owners over the API") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T16:40:00Z")

    val root = playerService(app).registerPlayer("ns-transfer-root", "NsTransferRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = playerService(app).registerPlayer("ns-transfer-owner", "NsTransferOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val suspended = playerService(app).registerPlayer("ns-transfer-suspended", "NsTransferSuspended", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val superAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    val suspendedMember = playerRepository(app).findById(suspended.id).getOrElse(fail("suspended member missing"))
    playerRepository(app).save(suspendedMember.copy(status = PlayerStatus.Suspended))

    withServer(app) { baseUrl =>
      val requestResponse = postJson(
        s"$baseUrl/dictionary/namespaces",
        write(
          RequestDictionaryNamespaceRequest(
            operatorId = owner.id.value,
            namespacePrefix = "ui.banner",
            note = Some("frontend banners")
          )
        )
      )
      assertEquals(requestResponse.statusCode(), 201)

      val reviewResponse = postJson(
        s"$baseUrl/dictionary/namespaces/review",
        write(
          ReviewDictionaryNamespaceRequest(
            operatorId = superAdmin.id.value,
            namespacePrefix = "ui.banner",
            approve = true,
            note = Some("approved")
          )
        )
      )
      assertEquals(reviewResponse.statusCode(), 200)

      val transferResponse = postJson(
        s"$baseUrl/dictionary/namespaces/transfer",
        write(
          TransferDictionaryNamespaceRequest(
            operatorId = superAdmin.id.value,
            namespacePrefix = "ui.banner",
            newOwnerPlayerId = suspended.id.value,
            note = Some("should fail")
          )
        )
      )
      assertEquals(transferResponse.statusCode(), 400)
      assert(transferResponse.body().contains("active player owner"))
    }
  }

  test("dictionary namespace backlog endpoints expose overdue triage data") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T16:10:00Z")
    val requestNow = Instant.now()
    val firstDueAt = requestNow.plusSeconds(1800)
    val secondDueAt = requestNow.plusSeconds(8 * 3600)
    val asOf = requestNow.plusSeconds(3 * 3600)

    val root = playerService(app).registerPlayer("ns-backlog-root", "NsBacklogRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val ownerA = playerService(app).registerPlayer("ns-backlog-owner-a", "NsBacklogOwnerA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val ownerB = playerService(app).registerPlayer("ns-backlog-owner-b", "NsBacklogOwnerB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val superAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))

    withServer(app) { baseUrl =>
      val firstRequest = postJson(
        s"$baseUrl/dictionary/namespaces",
        write(
          RequestDictionaryNamespaceRequest(
            operatorId = ownerA.id.value,
            namespacePrefix = "ui.alert",
            note = Some("alert family"),
            reviewDueAt = Some(firstDueAt.toString)
          )
        )
      )
      assertEquals(firstRequest.statusCode(), 201)

      val secondRequest = postJson(
        s"$baseUrl/dictionary/namespaces",
        write(
          RequestDictionaryNamespaceRequest(
            operatorId = ownerB.id.value,
            namespacePrefix = "ui.scoreboard",
            note = Some("scoreboard family"),
            reviewDueAt = Some(secondDueAt.toString)
          )
        )
      )
      assertEquals(secondRequest.statusCode(), 201)

      val backlogResponse = get(
        s"$baseUrl/dictionary/namespaces/backlog?operatorId=${superAdmin.id.value}&asOf=$asOf&dueSoonHours=8"
      )
      assertEquals(backlogResponse.statusCode(), 200)
      val backlog = read[DictionaryNamespaceBacklogView](backlogResponse.body())
      assertEquals(backlog.pendingCount, 2)
      assertEquals(backlog.overdueCount, 1)
      assertEquals(backlog.dueSoonCount, 1)
      assertEquals(backlog.ownerBacklog.map(_.ownerPlayerId), Vector(ownerA.id, ownerB.id))

      val overdueResponse = get(
        s"$baseUrl/dictionary/namespaces?operatorId=${superAdmin.id.value}&status=Pending&overdueOnly=true&asOf=$asOf"
      )
      assertEquals(overdueResponse.statusCode(), 200)
      val overduePage = readPage[DictionaryNamespaceRegistration](overdueResponse.body())
      assertEquals(overduePage.total, 1)
      assertEquals(overduePage.items.head.namespacePrefix, "ui.alert.")
    }
  }
