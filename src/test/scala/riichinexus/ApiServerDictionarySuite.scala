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
import riichinexus.microservices.dictionary.api.*
import riichinexus.microservices.dictionary.objects.apiTypes.UpsertDictionaryRequest
import riichinexus.microservices.opsanalytics.api.OpsAnalyticsListAuditsAPIMessage
import riichinexus.microservices.opsanalytics.objects.apiTypes.PerformanceDiagnosticsSnapshot
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest.given
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

class ApiServerDictionarySuite extends FunSuite with ApiServerSuiteSupport:
  test("dictionary and audit collection endpoints support filters and pagination") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:55:00Z")

    val root = playerService(app).registerPlayer("page-root", "Root", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val admin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    val adminPrincipal = principalFor(app, admin.id)

    dictionaryApi(app).requestDictionaryNamespace(
      namespacePrefix = "rank.formula",
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      requestedAt = now
    )
    dictionaryApi(app).requestDictionaryNamespace(
      namespacePrefix = "rank.scale",
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      requestedAt = now.plusSeconds(2)
    )
    dictionaryApi(app).requestDictionaryNamespace(
      namespacePrefix = "stage.schedulingpoolsize",
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      requestedAt = now.plusSeconds(3)
    )
    dictionaryApi(app).reviewDictionaryNamespace(
      namespacePrefix = "rank.formula",
      approve = true,
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      reviewedAt = now.plusSeconds(4)
    )
    dictionaryApi(app).reviewDictionaryNamespace(
      namespacePrefix = "rank.scale",
      approve = true,
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      reviewedAt = now.plusSeconds(5)
    )
    dictionaryApi(app).reviewDictionaryNamespace(
      namespacePrefix = "stage.schedulingpoolsize",
      approve = true,
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      reviewedAt = now.plusSeconds(6)
    )

    val rankFormula = dictionaryApi(app).upsertDictionary(
      key = "rank.formula.current",
      value = "uma+oka-v3",
      actor = adminPrincipal,
      updatedAt = now.plusSeconds(10)
    )
    dictionaryApi(app).upsertDictionary(
      key = "rank.scale.mode",
      value = "aggressive",
      actor = adminPrincipal,
      updatedAt = now.plusSeconds(60)
    )
    dictionaryApi(app).upsertDictionary(
      key = "stage.schedulingpoolsize.default",
      value = "4",
      actor = adminPrincipal,
      updatedAt = now.plusSeconds(120)
    )

    withServer(app) { baseUrl =>
      val dictionaryResponse = postApi(
        baseUrl,
        DictionaryListEntriesAPIMessage(prefix = Some("rank."), limit = Some(1))
      )
      assertEquals(dictionaryResponse.statusCode(), 200)
      val dictionaryPage = readPage[GlobalDictionaryEntry](dictionaryResponse.body())
      assertEquals(dictionaryPage.total, 2)
      assertEquals(dictionaryPage.items.size, 1)
      assertEquals(dictionaryPage.items.head.key, "rank.formula.current")

      val auditResponse = postJson(
        s"$baseUrl/api/opsanalyticslistauditsapi",
        write(
          OpsAnalyticsListAuditsAPIMessage(
            operatorId = admin.id,
            aggregateType = Some("dictionary"),
            actorId = Some(admin.id),
            limit = Some(1)
          )
        )
      )
      assertEquals(auditResponse.statusCode(), 200)
      val auditPage = readPage[AuditEventEntry](auditResponse.body())
      assertEquals(auditPage.total, 3)
      assertEquals(auditPage.items.head.aggregateId, rankFormula.key)
      assertEquals(auditPage.items.head.eventType, "GlobalDictionaryUpserted")
      assertEquals(auditPage.appliedFilters("aggregateType"), "dictionary")
    }
  }

  test("dictionary API changes default tournament settlement ratios at runtime") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T13:05:00Z")

    val root = playerService(app).registerPlayer("dict-api-root", "DictApiRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val superAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    val admin = playerService(app).registerPlayer("dict-api-admin", "DictApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("dict-api-b", "DictApiB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      playerService(app).registerPlayer("dict-api-c", "DictApiC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      playerService(app).registerPlayer("dict-api-d", "DictApiD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "API Settlement Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "API Dictionary Settlement Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    tableService(app).startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))
    tableService(app).recordCompletedTable(
      table.id,
      demoPaifuForResult(
        table,
        tournament.id,
        stage.id,
        now.plusSeconds(120),
        winner = table.seats.head.playerId,
        target = table.seats(1).playerId
      ),
      principalFor(app, admin.id)
    )
    tournamentService(app).completeStage(
      tournament.id,
      stage.id,
      principalFor(app, admin.id),
      now.plusSeconds(180)
    )

    withServer(app) { baseUrl =>
      val dictionaryResponse = postApi(
        baseUrl,
        DictionaryUpsertEntryAPIMessage(superAdmin.id.value, "settlement.defaultPayoutRatios", "0.6,0.25,0.15", Some("runtime payout tuning"))
      )
      assertEquals(dictionaryResponse.statusCode(), 201)

      val settleResponse = postApi(
        baseUrl,
        TournamentSettleAPIMessage(tournament.id.value, SettleTournamentRequest(admin.id.value, stage.id.value, 1000))
      )
      assertEquals(settleResponse.statusCode(), 200)
      val settlement = read[TournamentSettlementView](settleResponse.body())
      assertEquals(settlement.entries.take(3).map(_.awardAmount), Vector(600L, 250L, 150L))
    }
  }

