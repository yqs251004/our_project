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

class ApiServerDictionaryNamespaceWorkflowSuite extends FunSuite with ApiServerSuiteSupport:
  test("dictionary namespace workflow allows approved owners to write metadata keys") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T15:30:00Z")

    val root = playerService(app).registerPlayer("ns-root", "NsRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = playerService(app).registerPlayer("ns-owner", "NsOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val transferee = playerService(app).registerPlayer("ns-transferee", "NsTransferee", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val superAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))

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
      val pending = read[DictionaryNamespaceRegistration](requestResponse.body())
      assertEquals(pending.status, DictionaryNamespaceReviewStatus.Pending)
      assertEquals(pending.ownerPlayerId, owner.id)

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
      val approved = read[DictionaryNamespaceRegistration](reviewResponse.body())
      assertEquals(approved.status, DictionaryNamespaceReviewStatus.Approved)

      val upsertResponse = postJson(
        s"$baseUrl/admin/dictionary",
        write(
          UpsertDictionaryRequest(
            operatorId = owner.id.value,
            key = "ui.banner.message",
            value = "Spring finals this weekend",
            note = Some("owned metadata")
          )
        )
      )
      assertEquals(upsertResponse.statusCode(), 201)
      val metadata = read[GlobalDictionaryEntry](upsertResponse.body())
      assertEquals(metadata.key, "ui.banner.message")

      val transferResponse = postJson(
        s"$baseUrl/dictionary/namespaces/transfer",
        write(
          TransferDictionaryNamespaceRequest(
            operatorId = superAdmin.id.value,
            namespacePrefix = "ui.banner",
            newOwnerPlayerId = transferee.id.value,
            note = Some("handoff to content ops")
          )
        )
      )
      assertEquals(transferResponse.statusCode(), 200)
      val transferred = read[DictionaryNamespaceRegistration](transferResponse.body())
      assertEquals(transferred.ownerPlayerId, transferee.id)
      assertEquals(transferred.status, DictionaryNamespaceReviewStatus.Approved)

      val formerOwnerWrite = postJson(
        s"$baseUrl/admin/dictionary",
        write(
          UpsertDictionaryRequest(
            operatorId = owner.id.value,
            key = "ui.banner.message",
            value = "former owner co-owner write",
            note = Some("should still succeed")
          )
        )
      )
      assertEquals(formerOwnerWrite.statusCode(), 201)

      val transfereeWrite = postJson(
        s"$baseUrl/admin/dictionary",
        write(
          UpsertDictionaryRequest(
            operatorId = transferee.id.value,
            key = "ui.banner.message",
            value = "Transferred owner content",
            note = Some("new owner write")
          )
        )
      )
      assertEquals(transfereeWrite.statusCode(), 201)

      val revokeResponse = postJson(
        s"$baseUrl/dictionary/namespaces/revoke",
        write(
          RevokeDictionaryNamespaceRequest(
            operatorId = superAdmin.id.value,
            namespacePrefix = "ui.banner",
            note = Some("retired family")
          )
        )
      )
      assertEquals(revokeResponse.statusCode(), 200)
      val revoked = read[DictionaryNamespaceRegistration](revokeResponse.body())
      assertEquals(revoked.status, DictionaryNamespaceReviewStatus.Revoked)

      val revokedDenied = postJson(
        s"$baseUrl/admin/dictionary",
        write(
          UpsertDictionaryRequest(
            operatorId = transferee.id.value,
            key = "ui.banner.message",
            value = "revoked blocked",
            note = Some("should fail")
          )
        )
      )
      assertEquals(revokedDenied.statusCode(), 400)

      val listResponse = get(
        s"$baseUrl/dictionary/namespaces?operatorId=${superAdmin.id.value}&status=Revoked&reviewedBy=${superAdmin.id.value}"
      )
      assertEquals(listResponse.statusCode(), 200)
      val page = readPage[DictionaryNamespaceRegistration](listResponse.body())
      assertEquals(page.total, 1)
      assertEquals(page.items.head.namespacePrefix, "ui.banner.")
      assertEquals(page.items.head.ownerPlayerId, transferee.id)
    }
  }
