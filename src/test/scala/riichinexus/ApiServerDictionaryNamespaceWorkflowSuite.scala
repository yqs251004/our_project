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
import riichinexus.microservices.auth.objects.apiTypes.CreateGuestSessionRequest
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.objects.apiTypes.ClubTournamentResponses.given
import riichinexus.microservices.dictionary.api.*
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.DictionaryResponses.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.PerformanceDiagnosticsSnapshot
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
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
      val requestResponse = postApi(
        baseUrl,
        DictionaryRequestNamespaceAPIMessage(
          operatorId = owner.id.value,
          namespacePrefix = "ui.banner",
          note = Some("frontend banners")
        )
      )
      assertEquals(requestResponse.statusCode(), 201)
      val pending = read[DictionaryNamespaceRegistration](requestResponse.body())
      assertEquals(pending.status, DictionaryNamespaceReviewStatus.Pending)
      assertEquals(pending.ownerPlayerId, owner.id)

      val reviewResponse = postApi(
        baseUrl,
        DictionaryReviewNamespaceAPIMessage(
          operatorId = superAdmin.id.value,
          namespacePrefix = "ui.banner",
          approve = true,
          note = Some("approved")
        )
      )
      assertEquals(reviewResponse.statusCode(), 200)
      val approved = read[DictionaryNamespaceRegistration](reviewResponse.body())
      assertEquals(approved.status, DictionaryNamespaceReviewStatus.Approved)

      val upsertResponse = postApi(
        baseUrl,
        DictionaryUpsertEntryAPIMessage(
          operatorId = owner.id.value,
          key = "ui.banner.message",
          value = "Spring finals this weekend",
          note = Some("owned metadata")
        )
      )
      assertEquals(upsertResponse.statusCode(), 201)
      val metadata = read[GlobalDictionaryEntry](upsertResponse.body())
      assertEquals(metadata.key, "ui.banner.message")

      val transferResponse = postApi(
        baseUrl,
        DictionaryTransferNamespaceAPIMessage(
          operatorId = superAdmin.id.value,
          namespacePrefix = "ui.banner",
          newOwnerPlayerId = transferee.id.value,
          note = Some("handoff to content ops")
        )
      )
      assertEquals(transferResponse.statusCode(), 200)
      val transferred = read[DictionaryNamespaceRegistration](transferResponse.body())
      assertEquals(transferred.ownerPlayerId, transferee.id)
      assertEquals(transferred.status, DictionaryNamespaceReviewStatus.Approved)

      val formerOwnerWrite = postApi(
        baseUrl,
        DictionaryUpsertEntryAPIMessage(
          operatorId = owner.id.value,
          key = "ui.banner.message",
          value = "former owner co-owner write",
          note = Some("should still succeed")
        )
      )
      assertEquals(formerOwnerWrite.statusCode(), 201)

      val transfereeWrite = postApi(
        baseUrl,
        DictionaryUpsertEntryAPIMessage(
          operatorId = transferee.id.value,
          key = "ui.banner.message",
          value = "Transferred owner content",
          note = Some("new owner write")
        )
      )
      assertEquals(transfereeWrite.statusCode(), 201)

      val revokeResponse = postApi(
        baseUrl,
        DictionaryRevokeNamespaceAPIMessage(
          operatorId = superAdmin.id.value,
          namespacePrefix = "ui.banner",
          note = Some("retired family")
        )
      )
      assertEquals(revokeResponse.statusCode(), 200)
      val revoked = read[DictionaryNamespaceRegistration](revokeResponse.body())
      assertEquals(revoked.status, DictionaryNamespaceReviewStatus.Revoked)

      val revokedDenied = postApi(
        baseUrl,
        DictionaryUpsertEntryAPIMessage(
          operatorId = transferee.id.value,
          key = "ui.banner.message",
          value = "revoked blocked",
          note = Some("should fail")
        )
      )
      assertEquals(revokedDenied.statusCode(), 400)

      val listResponse = postApi(
        baseUrl,
        DictionaryListNamespacesAPIMessage(superAdmin.id.value, status = Some("Revoked"), reviewedBy = Some(superAdmin.id.value))
      )
      assertEquals(listResponse.statusCode(), 200)
      val page = readPage[DictionaryNamespaceRegistration](listResponse.body())
      assertEquals(page.total, 1)
      assertEquals(page.items.head.namespacePrefix, "ui.banner.")
      assertEquals(page.items.head.ownerPlayerId, transferee.id)
    }
  }
