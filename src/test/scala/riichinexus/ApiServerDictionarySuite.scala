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

class ApiServerDictionarySuite extends FunSuite with ApiServerSuiteSupport:
  test("dictionary and audit collection endpoints support filters and pagination") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:55:00Z")

    val root = playerService(app).registerPlayer("page-root", "Root", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val admin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    val adminPrincipal = principalFor(app, admin.id)

    dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "rank.formula",
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      requestedAt = now
    )
    dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "rank.scale",
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      requestedAt = now.plusSeconds(2)
    )
    dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "stage.schedulingpoolsize",
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      requestedAt = now.plusSeconds(3)
    )
    dictionaryGovernance(app).reviewDictionaryNamespace(
      namespacePrefix = "rank.formula",
      approve = true,
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      reviewedAt = now.plusSeconds(4)
    )
    dictionaryGovernance(app).reviewDictionaryNamespace(
      namespacePrefix = "rank.scale",
      approve = true,
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      reviewedAt = now.plusSeconds(5)
    )
    dictionaryGovernance(app).reviewDictionaryNamespace(
      namespacePrefix = "stage.schedulingpoolsize",
      approve = true,
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      reviewedAt = now.plusSeconds(6)
    )

    val rankFormula = dictionaryGovernance(app).upsertDictionary(
      key = "rank.formula.current",
      value = "uma+oka-v3",
      actor = adminPrincipal,
      updatedAt = now.plusSeconds(10)
    )
    dictionaryGovernance(app).upsertDictionary(
      key = "rank.scale.mode",
      value = "aggressive",
      actor = adminPrincipal,
      updatedAt = now.plusSeconds(60)
    )
    dictionaryGovernance(app).upsertDictionary(
      key = "stage.schedulingpoolsize.default",
      value = "4",
      actor = adminPrincipal,
      updatedAt = now.plusSeconds(120)
    )

    withServer(app) { baseUrl =>
      val dictionaryResponse = get(s"$baseUrl/dictionary?prefix=rank.&limit=1")
      assertEquals(dictionaryResponse.statusCode(), 200)
      val dictionaryPage = readPage[GlobalDictionaryEntry](dictionaryResponse.body())
      assertEquals(dictionaryPage.total, 2)
      assertEquals(dictionaryPage.items.size, 1)
      assertEquals(dictionaryPage.items.head.key, "rank.formula.current")

      val auditResponse = get(
        s"$baseUrl/audits?operatorId=${admin.id.value}&aggregateType=dictionary&actorId=${admin.id.value}&limit=1"
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
      val dictionaryResponse = postJson(
        s"$baseUrl/admin/dictionary",
        write(UpsertDictionaryRequest(superAdmin.id.value, "settlement.defaultPayoutRatios", "0.6,0.25,0.15", Some("runtime payout tuning")))
      )
      assertEquals(dictionaryResponse.statusCode(), 201)

      val settleResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/settle",
        write(SettleTournamentRequest(admin.id.value, stage.id.value, 1000))
      )
      assertEquals(settleResponse.statusCode(), 200)
      val settlement = read[TournamentSettlementSnapshot](settleResponse.body())
      assertEquals(settlement.entries.take(3).map(_.awardAmount), Vector(600L, 250L, 150L))
    }
  }

