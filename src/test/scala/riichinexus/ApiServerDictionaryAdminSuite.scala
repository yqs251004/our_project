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
import riichinexus.microservices.opsanalytics.api.OpsAnalyticsListAggregateAuditsAPIMessage
import riichinexus.microservices.opsanalytics.objects.apiTypes.PerformanceDiagnosticsSnapshot
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

class ApiServerDictionaryAdminSuite extends FunSuite with ApiServerSuiteSupport:
  test("admin dictionary rejects unknown reserved runtime namespace keys") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T15:20:00Z")

    val root = playerService(app).registerPlayer("runtime-root", "RuntimeRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val admin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))

    withServer(app) { baseUrl =>
      val response = postApi(
        baseUrl,
        DictionaryUpsertEntryAPIMessage(
          operatorId = admin.id.value,
          key = "rating.elo.experimentalFactor",
          value = "72",
          note = Some("should fail until registered")
        )
      )
      assertEquals(response.statusCode(), 400)
      assert(response.body().contains("reserved runtime namespace"))
    }
  }

  test("dictionary key and audit aggregate endpoints return targeted admin data") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:00:00Z")

    val root = playerService(app).registerPlayer("root-1", "Root", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val viewer = playerService(app).registerPlayer("root-2", "Viewer", RankSnapshot(RankPlatform.Custom, "A"), now, 1700)
    val admin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    val adminPrincipal = principalFor(app, admin.id)

    dictionaryApi(app).requestDictionaryNamespace(
      namespacePrefix = "rank.formula",
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      requestedAt = now
    )
    dictionaryApi(app).reviewDictionaryNamespace(
      namespacePrefix = "rank.formula",
      approve = true,
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      reviewedAt = now.plusSeconds(1)
    )

    val dictionaryEntry = dictionaryApi(app).upsertDictionary(
      key = "rank.formula.current",
      value = "uma+oka-v2",
      actor = adminPrincipal,
      note = Some("season 2"),
      updatedAt = now.plusSeconds(10)
    )

    withServer(app) { baseUrl =>
      val dictionaryResponse = postApi(
        baseUrl,
        DictionaryGetEntryAPIMessage(dictionaryEntry.key)
      )
      assertEquals(dictionaryResponse.statusCode(), 200)
      val storedEntry = read[GlobalDictionaryEntry](dictionaryResponse.body())
      assertEquals(storedEntry.value, dictionaryEntry.value)

      val forbiddenAudit = postJson(
        s"$baseUrl/api/opsanalyticslistaggregateauditsapi",
        write(
          OpsAnalyticsListAggregateAuditsAPIMessage(
            operatorId = viewer.id,
            aggregateType = "dictionary",
            aggregateId = dictionaryEntry.key
          )
        )
      )
      assertEquals(forbiddenAudit.statusCode(), 403)

      val auditResponse = postJson(
        s"$baseUrl/api/opsanalyticslistaggregateauditsapi",
        write(
          OpsAnalyticsListAggregateAuditsAPIMessage(
            operatorId = admin.id,
            aggregateType = "dictionary",
            aggregateId = dictionaryEntry.key,
            eventType = Some("GlobalDictionaryUpserted")
          )
        )
      )
      assertEquals(auditResponse.statusCode(), 200)
      val auditEntries = readPage[AuditEventEntry](auditResponse.body())
      assertEquals(auditEntries.total, 1)
      assertEquals(auditEntries.items.head.aggregateId, dictionaryEntry.key)
      assertEquals(auditEntries.items.head.eventType, "GlobalDictionaryUpserted")
    }
  }
