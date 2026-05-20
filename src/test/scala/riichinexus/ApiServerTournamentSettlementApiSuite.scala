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
import riichinexus.microservices.dictionary.api.*
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

    val club = clubApi(app).createClub("Settlement API Club", admin.id, now, admin.asPrincipal)
    players.tail.foreach(player => clubApi(app).addMember(club.id, player.id, principalFor(app, admin.id)))

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
      val draftResponse = postApi(
        baseUrl,
        TournamentSettleAPIMessage(
          tournament.id.value,
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
      val draft = read[TournamentSettlementView](draftResponse.body())
      assertEquals(draft.status, TournamentSettlementStatus.Draft)
      assertEquals(draft.revision, 1)
      assertEquals(draft.houseFeeAmount, 100L)
      assertEquals(draft.entries.head.baseAwardAmount, 540L)

      val finalizeResponse = postApi(
        baseUrl,
        TournamentSettlementFinalizeAPIMessage(
          tournament.id.value,
          draft.settlementId.value,
          FinalizeTournamentSettlementRequest(admin.id.value, Some("finance approved"))
        )
      )
      assertEquals(finalizeResponse.statusCode(), 200)
      val finalized = read[TournamentSettlementView](finalizeResponse.body())
      assertEquals(finalized.status, TournamentSettlementStatus.Finalized)

      val revisedResponse = postApi(
        baseUrl,
        TournamentSettleAPIMessage(
          tournament.id.value,
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
      val revised = read[TournamentSettlementView](revisedResponse.body())
      assertEquals(revised.revision, 2)
      assertEquals(revised.status, TournamentSettlementStatus.Finalized)
      assertEquals(revised.supersedesSettlementId, Some(draft.settlementId))

      val settlementIndex = postApi(
        baseUrl,
        TournamentSettlementListAPIMessage(tournament.id.value, status = Some("Superseded"))
      )
      assertEquals(settlementIndex.statusCode(), 200)
      val supersededPage = readPage[TournamentSettlementView](settlementIndex.body())
      assertEquals(supersededPage.total, 1)
      assertEquals(supersededPage.items.head.settlementId, draft.settlementId)

      val latestResponse = postApi(
        baseUrl,
        TournamentSettlementGetAPIMessage(tournament.id.value, stage.id.value)
      )
      assertEquals(latestResponse.statusCode(), 200)
      assertEquals(read[TournamentSettlementView](latestResponse.body()).settlementId, revised.settlementId)

      val auditPage = postApi(
        baseUrl,
        TournamentSettlementListAPIMessage(tournament.id.value, status = Some("Finalized"), championId = Some(revised.championId.value))
      )
      assertEquals(auditPage.statusCode(), 200)
      assert(readPage[TournamentSettlementView](auditPage.body()).items.exists(_.settlementId == revised.settlementId))

      val runtimeDictionaryResponse = postApi(
        baseUrl,
        DictionaryUpsertEntryAPIMessage(superAdmin.id.value, "settlement.defaultPayoutRatios", "0.6,0.25,0.15", Some("runtime payout tuning"))
      )
      assertEquals(runtimeDictionaryResponse.statusCode(), 201)
    }
  }
