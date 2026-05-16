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

class ApiServerAppealApiSuite extends FunSuite with ApiServerSuiteSupport:

  test("record and appeal detail endpoints return persisted workflow objects") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T10:00:00Z")

    val admin = playerService(app).registerPlayer("admin-2", "Admin2", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("q2", "Echo", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      playerService(app).registerPlayer("q3", "Foxtrot", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620),
      playerService(app).registerPlayer("q4", "Golf", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Operational Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Operations Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player =>
      tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id))
    )
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    val table = tournamentService(app).scheduleStageTables(
      tournament.id,
      stage.id,
      principalFor(app, admin.id)
    ).head

    tableService(app).startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))

    val appeal = appealService(app).fileAppeal(
      tableId = table.id,
      openedBy = players(1).id,
      description = "disconnect happened",
      actor = principalFor(app, players(1).id),
      createdAt = now.plusSeconds(90)
    ).getOrElse(fail("appeal was not created"))

    val adminPrincipal = principalFor(app, admin.id)
    appealService(app).adjudicateAppeal(
      ticketId = appeal.id,
      decision = AppealDecisionType.Resolve,
      verdict = "table reset approved",
      actor = adminPrincipal,
      tableResolution = Some(AppealTableResolution.ForceReset),
      adjudicatedAt = now.plusSeconds(120)
    )

    val resetTable = tableRepository(app).findById(table.id).getOrElse(fail("table missing after reset"))
    tableService(app).startTable(resetTable.id, now.plusSeconds(180), adminPrincipal)
    tableService(app).recordCompletedTable(
      resetTable.id,
      demoPaifuForResult(
        resetTable,
        tournament.id,
        stage.id,
        now.plusSeconds(240),
        winner = players(1).id,
        target = players(2).id
      ),
      adminPrincipal
    )

    val record = matchRecordRepository(app).findByTable(table.id).getOrElse(fail("match record missing"))

    withServer(app) { baseUrl =>
      val appealResponse = get(s"$baseUrl/appeals/${appeal.id.value}")
      assertEquals(appealResponse.statusCode(), 200)
      val storedAppeal = read[AppealTicket](appealResponse.body())
      assertEquals(storedAppeal.id, appeal.id)
      assertEquals(storedAppeal.status, AppealStatus.Resolved)

      val recordByIdResponse = get(s"$baseUrl/records/${record.id.value}")
      assertEquals(recordByIdResponse.statusCode(), 200)
      val recordById = read[MatchRecord](recordByIdResponse.body())
      assertEquals(recordById.id, record.id)

      val recordByTableResponse = get(s"$baseUrl/records/table/${table.id.value}")
      assertEquals(recordByTableResponse.statusCode(), 200)
      val recordByTable = read[MatchRecord](recordByTableResponse.body())
      assertEquals(recordByTable.id, record.id)
    }
  }

  test("appeal workflow endpoints support triage filters and reopen flow") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T10:20:00Z")

    val admin = playerService(app).registerPlayer("api-appeal-admin", "ApiAppealAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1810)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("api-appeal-b", "ApiAppealB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      playerService(app).registerPlayer("api-appeal-c", "ApiAppealC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      playerService(app).registerPlayer("api-appeal-d", "ApiAppealD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "API Appeal Workflow", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "API Appeal Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player =>
      tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id))
    )
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    val table = tournamentService(app).scheduleStageTables(
      tournament.id,
      stage.id,
      principalFor(app, admin.id)
    ).head
    tableService(app).startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))
    val openerId = table.seats.head.playerId

    val appeal = appealService(app).fileAppeal(
      tableId = table.id,
      openedBy = openerId,
      description = "disconnect happened again",
      priority = AppealPriority.High,
      dueAt = Some(now.plusSeconds(180)),
      actor = principalFor(app, openerId),
      createdAt = now.plusSeconds(90)
    ).getOrElse(fail("appeal was not created"))
    assertEquals(appeal.priority, AppealPriority.High)

    withServer(app) { baseUrl =>
      val workflowDueAt = Instant.now().plusSeconds(600)
      val workflowResponse = postJson(
        s"$baseUrl/appeals/${appeal.id.value}/workflow",
        write(
          UpdateAppealWorkflowRequest(
            operatorId = admin.id.value,
            assigneeId = Some(admin.id.value),
            priority = Some("Critical"),
            dueAt = Some(workflowDueAt.toString),
            note = Some("expedite this table")
          )
        )
      )
      assertEquals(workflowResponse.statusCode(), 200)
      val triaged = read[AppealTicket](workflowResponse.body())
      assertEquals(triaged.assigneeId, Some(admin.id))
      assertEquals(triaged.priority, AppealPriority.Critical)

      val filteredResponse = get(
        s"$baseUrl/appeals?priority=Critical&assigneeId=${admin.id.value}&overdueOnly=true&asOf=${workflowDueAt.plusSeconds(60)}"
      )
      assertEquals(filteredResponse.statusCode(), 200)
      val filteredPage = readPage[AppealTicket](filteredResponse.body())
      assertEquals(filteredPage.total, 1)
      assertEquals(filteredPage.items.head.id, appeal.id)

      val rejectResponse = postJson(
        s"$baseUrl/appeals/${appeal.id.value}/adjudicate",
        write(
          AdjudicateAppealRequest(
            operatorId = admin.id.value,
            decision = "Reject",
            verdict = "need stronger evidence",
            note = Some("reopen if more logs arrive")
          )
        )
      )
      assertEquals(rejectResponse.statusCode(), 200)
      assertEquals(read[AppealTicket](rejectResponse.body()).status, AppealStatus.Rejected)

      val reopenResponse = postJson(
        s"$baseUrl/appeals/${appeal.id.value}/reopen",
        write(
          ReopenAppealRequest(
            operatorId = openerId.value,
            reason = "new screenshot uploaded",
            note = Some("please review updated proof")
          )
        )
      )
      assertEquals(reopenResponse.statusCode(), 200)
      val reopened = read[AppealTicket](reopenResponse.body())
      assertEquals(reopened.status, AppealStatus.Open)
      assertEquals(reopened.reopenCount, 1)
      assertEquals(reopened.assigneeId, Some(admin.id))

      val detailResponse = get(s"$baseUrl/appeals/${appeal.id.value}")
      assertEquals(detailResponse.statusCode(), 200)
      assertEquals(read[AppealTicket](detailResponse.body()).reopenCount, 1)
    }
  }
