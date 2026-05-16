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

class ApiServerTournamentSettlementApiSuite extends FunSuite with ApiServerSuiteSupport:
  test("tournament settlement API supports draft revisions finalization and status filters") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:40:00Z")

    val superRoot = playerService(app).registerPlayer("settle-api-root", "SettleApiRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val superAdmin = playerRepository(app).save(superRoot.grantRole(RoleGrant.superAdmin(now)))
    val admin = playerService(app).registerPlayer("settle-api-admin", "SettleApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("settle-api-b", "SettleApiB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      playerService(app).registerPlayer("settle-api-c", "SettleApiC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      playerService(app).registerPlayer("settle-api-d", "SettleApiD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val club = clubService(app).createClub("Settlement API Club", admin.id, now, admin.asPrincipal)
    players.tail.foreach(player => clubService(app).addMember(club.id, player.id, principalFor(app, admin.id)))

    val stage = TournamentStage(IdGenerator.stageId(), "Settlement API Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Settlement API Cup",
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
      val draftResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/settle",
        write(
          SettleTournamentRequest(
            operatorId = admin.id.value,
            finalStageId = stage.id.value,
            prizePool = 1000,
            payoutRatios = Vector(0.6, 0.25, 0.15),
            houseFeeAmount = 100,
            clubShareRatio = 0.2,
            adjustments = Vector(
              SettlementAdjustmentRequest(players(1).id.value, "sportsmanship-award", 50L),
              SettlementAdjustmentRequest(players(2).id.value, "late-penalty", -10L)
            ),
            finalizeSettlement = false,
            note = Some("draft payout")
          )
        )
      )
      assertEquals(draftResponse.statusCode(), 200)
      val draft = read[TournamentSettlementSnapshot](draftResponse.body())
      assertEquals(draft.status, TournamentSettlementStatus.Draft)
      assertEquals(draft.revision, 1)
      assertEquals(draft.houseFeeAmount, 100L)
      assertEquals(draft.entries.head.baseAwardAmount, 540L)

      val finalizeResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/settlements/${draft.id.value}/finalize",
        write(FinalizeTournamentSettlementRequest(admin.id.value, Some("finance approved")))
      )
      assertEquals(finalizeResponse.statusCode(), 200)
      val finalized = read[TournamentSettlementSnapshot](finalizeResponse.body())
      assertEquals(finalized.status, TournamentSettlementStatus.Finalized)

      val revisedResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/settle",
        write(
          SettleTournamentRequest(
            operatorId = admin.id.value,
            finalStageId = stage.id.value,
            prizePool = 1100,
            payoutRatios = Vector(0.5, 0.3, 0.2),
            houseFeeAmount = 110,
            clubShareRatio = 0.1,
            adjustments = Vector(SettlementAdjustmentRequest(players(3).id.value, "stream-feature-bonus", 40L)),
            finalizeSettlement = true,
            note = Some("revised payout")
          )
        )
      )
      assertEquals(revisedResponse.statusCode(), 200)
      val revised = read[TournamentSettlementSnapshot](revisedResponse.body())
      assertEquals(revised.revision, 2)
      assertEquals(revised.status, TournamentSettlementStatus.Finalized)
      assertEquals(revised.supersedesSettlementId, Some(draft.id))

      val settlementIndex = get(
        s"$baseUrl/tournaments/${tournament.id.value}/settlements?status=Superseded"
      )
      assertEquals(settlementIndex.statusCode(), 200)
      val supersededPage = readPage[TournamentSettlementSnapshot](settlementIndex.body())
      assertEquals(supersededPage.total, 1)
      assertEquals(supersededPage.items.head.id, draft.id)

      val latestResponse = get(
        s"$baseUrl/tournaments/${tournament.id.value}/settlements/${stage.id.value}"
      )
      assertEquals(latestResponse.statusCode(), 200)
      assertEquals(read[TournamentSettlementSnapshot](latestResponse.body()).id, revised.id)

      val auditPage = get(
        s"$baseUrl/tournaments/${tournament.id.value}/settlements?status=Finalized&championId=${revised.championId.value}"
      )
      assertEquals(auditPage.statusCode(), 200)
      assert(readPage[TournamentSettlementSnapshot](auditPage.body()).items.exists(_.id == revised.id))

      val runtimeDictionaryResponse = postJson(
        s"$baseUrl/admin/dictionary",
        write(UpsertDictionaryRequest(superAdmin.id.value, "settlement.defaultPayoutRatios", "0.6,0.25,0.15", Some("runtime payout tuning")))
      )
      assertEquals(runtimeDictionaryResponse.statusCode(), 201)
    }
  }
