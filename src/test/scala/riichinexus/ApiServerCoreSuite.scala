package riichinexus

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

import munit.FunSuite

import riichinexus.api.*
import riichinexus.api.ApiModels.given
import riichinexus.application.service.PerformanceDiagnosticsSnapshot
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

class ApiServerCoreSuite extends FunSuite with ApiServerSuiteSupport:

  test("guest session endpoints support anonymous club applications") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T08:30:00Z")

    val owner = app.playerService.registerPlayer("guest-api-owner", "GuestApiOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = app.clubService.createClub("Guest API Club", owner.id, now, owner.asPrincipal)

    withServer(app) { baseUrl =>
      val createResponse = postJson(
        s"$baseUrl/guest-sessions",
        write(CreateGuestSessionRequest(Some("AnonymousFan")))
      )
      assertEquals(createResponse.statusCode(), 201)
      val session = read[GuestAccessSession](createResponse.body())
      assertEquals(session.displayName, "AnonymousFan")

      val detailResponse = get(s"$baseUrl/guest-sessions/${session.id.value}")
      assertEquals(detailResponse.statusCode(), 200)
      assertEquals(read[GuestAccessSession](detailResponse.body()).id, session.id)

      val applicationResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/applications",
        write(
          ClubMembershipApplicationRequest(
            applicantUserId = None,
            displayName = "ignored-by-session",
            message = Some("guest route test"),
            guestSessionId = Some(session.id.value)
          )
        )
      )
      assertEquals(applicationResponse.statusCode(), 200)
      val application = read[ClubMembershipApplication](applicationResponse.body())
      assertEquals(application.displayName, "AnonymousFan")
      assertEquals(application.applicantUserId, Some(s"guest:${session.id.value}"))

      val listResponse = get(s"$baseUrl/clubs/${club.id.value}/applications?operatorId=${owner.id.value}")
      assertEquals(listResponse.statusCode(), 200)
      val applications = readPage[ClubMembershipApplicationView](listResponse.body())
      assertEquals(applications.total, 1)
      assertEquals(applications.items.head.applicationId, application.id)
    }
  }

  test("registered players can submit and withdraw club applications over the API") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T08:45:00Z")

    val owner = app.playerService.registerPlayer("api-withdraw-owner", "ApiWithdrawOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val applicant = app.playerService.registerPlayer("api-withdraw-player", "ApiWithdrawPlayer", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val club = app.clubService.createClub("Withdraw API Club", owner.id, now, owner.asPrincipal)

    withServer(app) { baseUrl =>
      val createResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/applications",
        write(
          ClubMembershipApplicationRequest(
            applicantUserId = None,
            displayName = "fallback-name",
            message = Some("registered path"),
            guestSessionId = None,
            operatorId = Some(applicant.id.value)
          )
        )
      )
      assertEquals(createResponse.statusCode(), 200)
      val created = read[ClubMembershipApplication](createResponse.body())
      assertEquals(created.applicantUserId, Some(applicant.userId))
      assertEquals(created.displayName, applicant.nickname)

      val withdrawResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/applications/${created.id.value}/withdraw",
        write(WithdrawClubApplicationRequest(operatorId = Some(applicant.id.value), note = Some("not this season")))
      )
      assertEquals(withdrawResponse.statusCode(), 200)
      val withdrawn = read[ClubMembershipApplication](withdrawResponse.body())
      assertEquals(withdrawn.status, ClubMembershipApplicationStatus.Withdrawn)
      assertEquals(withdrawn.withdrawnByPrincipalId, Some(applicant.id.value))

      val filteredResponse = get(
        s"$baseUrl/clubs/${club.id.value}/applications?operatorId=${owner.id.value}&status=Withdrawn&applicantUserId=${applicant.userId}"
      )
      assertEquals(filteredResponse.statusCode(), 200)
      val applications = readPage[ClubMembershipApplicationView](filteredResponse.body())
      assertEquals(applications.total, 1)
      assertEquals(applications.items.head.applicationId, created.id)
    }
  }

  test("club applications endpoint returns submitted applications") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T08:00:00Z")

    val owner = app.playerService.registerPlayer("owner-1", "Owner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = app.clubService.createClub("Application Club", owner.id, now, owner.asPrincipal)

    app.clubService.applyForMembership(
      clubId = club.id,
      applicantUserId = Some("guest-1"),
      displayName = "Guest Applicant",
      message = Some("let me in")
    )

    withServer(app) { baseUrl =>
      val response = get(s"$baseUrl/clubs/${club.id.value}/applications?operatorId=${owner.id.value}")
      assertEquals(response.statusCode(), 200)

      val applications = readPage[ClubMembershipApplicationView](response.body())
      assertEquals(applications.total, 1)
      assertEquals(applications.items.head.applicant.displayName, "Guest Applicant")
      assertEquals(applications.items.head.message, Some("let me in"))
    }
  }

  test("tournament whitelist and stage tables endpoints return seeded competition data") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T09:00:00Z")

    val admin = app.playerService.registerPlayer("admin-1", "Admin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)
    val players = Vector(
      admin,
      app.playerService.registerPlayer("p2", "Bravo", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      app.playerService.registerPlayer("p3", "Charlie", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620),
      app.playerService.registerPlayer("p4", "Delta", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    )

    val club = app.clubService.createClub("Whitelist Club", admin.id, now, admin.asPrincipal)
    players.tail.foreach(player =>
      app.clubService.addMember(club.id, player.id, principalFor(app, admin.id))
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Swiss Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Whitelist Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    app.tournamentService.whitelistClub(tournament.id, club.id, principalFor(app, admin.id))
    players.foreach(player =>
      app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id))
    )
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))
    app.tournamentService.scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id))

    withServer(app) { baseUrl =>
      val whitelistResponse = get(s"$baseUrl/tournaments/${tournament.id.value}/whitelist")
      assertEquals(whitelistResponse.statusCode(), 200)
      val whitelist = readPage[TournamentWhitelistEntry](whitelistResponse.body())
      assert(whitelist.items.exists(_.clubId.contains(club.id)))
      assertEquals(
        whitelist.items.count(_.participantKind == TournamentParticipantKind.Player),
        players.size
      )

      val tablesResponse = get(s"$baseUrl/tournaments/${tournament.id.value}/stages/${stage.id.value}/tables")
      assertEquals(tablesResponse.statusCode(), 200)
      val tables = readPage[Table](tablesResponse.body())
      assertEquals(tables.total, 1)
      assertEquals(tables.items.head.seats.map(_.playerId).toSet, players.map(_.id).toSet)
    }
  }

  test("record and appeal detail endpoints return persisted workflow objects") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T10:00:00Z")

    val admin = app.playerService.registerPlayer("admin-2", "Admin2", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)
    val players = Vector(
      admin,
      app.playerService.registerPlayer("q2", "Echo", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650),
      app.playerService.registerPlayer("q3", "Foxtrot", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620),
      app.playerService.registerPlayer("q4", "Golf", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Operational Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Operations Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player =>
      app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id))
    )
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))
    val table = app.tournamentService.scheduleStageTables(
      tournament.id,
      stage.id,
      principalFor(app, admin.id)
    ).head

    app.tableService.startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))

    val appeal = app.appealService.fileAppeal(
      tableId = table.id,
      openedBy = players(1).id,
      description = "disconnect happened",
      actor = principalFor(app, players(1).id),
      createdAt = now.plusSeconds(90)
    ).getOrElse(fail("appeal was not created"))

    val adminPrincipal = principalFor(app, admin.id)
    app.appealService.adjudicateAppeal(
      ticketId = appeal.id,
      decision = AppealDecisionType.Resolve,
      verdict = "table reset approved",
      actor = adminPrincipal,
      tableResolution = Some(AppealTableResolution.ForceReset),
      adjudicatedAt = now.plusSeconds(120)
    )

    val resetTable = app.tableRepository.findById(table.id).getOrElse(fail("table missing after reset"))
    app.tableService.startTable(resetTable.id, now.plusSeconds(180), adminPrincipal)
    app.tableService.recordCompletedTable(
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

    val record = app.matchRecordRepository.findByTable(table.id).getOrElse(fail("match record missing"))

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

    val admin = app.playerService.registerPlayer("api-appeal-admin", "ApiAppealAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1810)
    val players = Vector(
      admin,
      app.playerService.registerPlayer("api-appeal-b", "ApiAppealB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      app.playerService.registerPlayer("api-appeal-c", "ApiAppealC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      app.playerService.registerPlayer("api-appeal-d", "ApiAppealD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "API Appeal Workflow", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "API Appeal Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player =>
      app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id))
    )
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))
    val table = app.tournamentService.scheduleStageTables(
      tournament.id,
      stage.id,
      principalFor(app, admin.id)
    ).head
    app.tableService.startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))
    val openerId = table.seats.head.playerId

    val appeal = app.appealService.fileAppeal(
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

  test("dashboard endpoints enforce RBAC and allow scoped access") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T11:00:00Z")

    val owner = app.playerService.registerPlayer("dash-1", "Owner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val intruder = app.playerService.registerPlayer("dash-2", "Intruder", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val member = app.playerService.registerPlayer("dash-3", "Member", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1520)

    val club = app.clubService.createClub("Dashboard Club", owner.id, now, owner.asPrincipal)
    app.clubService.addMember(club.id, member.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val forbiddenPlayer = get(
        s"$baseUrl/dashboards/players/${owner.id.value}?operatorId=${intruder.id.value}"
      )
      assertEquals(forbiddenPlayer.statusCode(), 403)

      val ownPlayer = get(
        s"$baseUrl/dashboards/players/${owner.id.value}?operatorId=${owner.id.value}"
      )
      assertEquals(ownPlayer.statusCode(), 200)

      val ownClub = get(
        s"$baseUrl/dashboards/clubs/${club.id.value}?operatorId=${owner.id.value}"
      )
      assertEquals(ownClub.statusCode(), 200)

      val forbiddenClub = get(
        s"$baseUrl/dashboards/clubs/${club.id.value}?operatorId=${member.id.value}"
      )
      assertEquals(forbiddenClub.statusCode(), 403)
    }
  }

  test("advanced stats endpoints expose dedicated boards with dashboard RBAC") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T11:45:00Z")

    val owner = app.playerService.registerPlayer("adv-owner", "AdvOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val root = app.playerService.registerPlayer("adv-root", "AdvRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val rootAdmin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))
    val member = app.playerService.registerPlayer("adv-member", "AdvMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1520)
    val intruder = app.playerService.registerPlayer("adv-intruder", "AdvIntruder", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1490)
    val extraA = app.playerService.registerPlayer("adv-extra-a", "AdvExtraA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1510)
    val extraB = app.playerService.registerPlayer("adv-extra-b", "AdvExtraB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)

    val club = app.clubService.createClub("Advanced Stats Club", owner.id, now, owner.asPrincipal)
    app.clubService.addMember(club.id, member.id, principalFor(app, owner.id))
    app.clubService.addMember(club.id, extraA.id, principalFor(app, owner.id))
    app.clubService.addMember(club.id, extraB.id, principalFor(app, owner.id))

    val stage = TournamentStage(IdGenerator.stageId(), "Advanced Stats Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Advanced Stats Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )
    Vector(owner, member, extraA, extraB).foreach(player => app.tournamentService.registerPlayer(tournament.id, player.id))
    app.tournamentService.publishTournament(tournament.id)

    val table = app.tournamentService.scheduleStageTables(tournament.id, stage.id).head
    app.tableService.startTable(table.id, now.plusSeconds(60))
    app.tableService.recordCompletedTable(
      table.id,
      demoPaifuForResult(
        table,
        tournament.id,
        stage.id,
        now.plusSeconds(120),
        winner = table.seats.head.playerId,
        target = table.seats(1).playerId
      )
    )

    withServer(app) { baseUrl =>
      val taskIndex = get(
        s"$baseUrl/admin/advanced-stats/tasks?operatorId=${rootAdmin.id.value}"
      )
      assertEquals(taskIndex.statusCode(), 200)
      assert(readPage[AdvancedStatsRecomputeTask](taskIndex.body()).total >= 0)

      val recomputeTasks = postJson(
        s"$baseUrl/admin/advanced-stats/recompute",
        write(
          RecomputeAdvancedStatsRequest(
            operatorId = rootAdmin.id.value,
            ownerType = Some("player"),
            ownerId = Some(owner.id.value),
            reason = Some("api-test-backfill")
          )
        )
      )
      assertEquals(recomputeTasks.statusCode(), 202)
      assert(read[Vector[AdvancedStatsRecomputeTask]](recomputeTasks.body()).nonEmpty)

      val taskIndexAfter = get(
        s"$baseUrl/admin/advanced-stats/tasks?operatorId=${rootAdmin.id.value}"
      )
      assertEquals(taskIndexAfter.statusCode(), 200)
      assert(readPage[AdvancedStatsRecomputeTask](taskIndexAfter.body()).total > 0)

      val processTasks = postJson(
        s"$baseUrl/admin/advanced-stats/process",
        write(ProcessAdvancedStatsTasksRequest(rootAdmin.id.value, 20))
      )
      assertEquals(processTasks.statusCode(), 200)
      app.advancedStatsPipelineService.enqueueOwnerRecompute(DashboardOwner.Player(owner.id), "api-test-player-read")
      app.advancedStatsPipelineService.enqueueOwnerRecompute(DashboardOwner.Club(club.id), "api-test-club-read")
      app.advancedStatsPipelineService.processPending(limit = 20, processedAt = Instant.now())

      val ownPlayerStats = get(
        s"$baseUrl/advanced-stats/players/${owner.id.value}?operatorId=${owner.id.value}"
      )
      assertEquals(ownPlayerStats.statusCode(), 200)
      val playerBoard = read[AdvancedStatsBoard](ownPlayerStats.body())
      assert(playerBoard.sampleSize > 0)

      val ownClubStats = get(
        s"$baseUrl/advanced-stats/clubs/${club.id.value}?operatorId=${owner.id.value}"
      )
      assertEquals(ownClubStats.statusCode(), 200)
      val clubBoard = read[AdvancedStatsBoard](ownClubStats.body())
      assert(clubBoard.sampleSize > 0)

      val forbiddenPlayerStats = get(
        s"$baseUrl/advanced-stats/players/${owner.id.value}?operatorId=${intruder.id.value}"
      )
      assertEquals(forbiddenPlayerStats.statusCode(), 403)

      val forbiddenClubStats = get(
        s"$baseUrl/advanced-stats/clubs/${club.id.value}?operatorId=${member.id.value}"
      )
      assertEquals(forbiddenClubStats.statusCode(), 403)
    }
  }

  test("advanced stats admin endpoints expose summary and missing-only backfill") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:05:00Z")

    val root = app.playerService.registerPlayer("adv-summary-root", "AdvSummaryRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val rootAdmin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))
    val playerA = app.playerService.registerPlayer("adv-summary-a", "AdvSummaryA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val playerB = app.playerService.registerPlayer("adv-summary-b", "AdvSummaryB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    app.advancedStatsBoardRepository.save(AdvancedStatsBoard.empty(DashboardOwner.Player(playerA.id), now))

    withServer(app) { baseUrl =>
      val recomputeResponse = postJson(
        s"$baseUrl/admin/advanced-stats/recompute",
        write(
          RecomputeAdvancedStatsRequest(
            operatorId = rootAdmin.id.value,
            mode = AdvancedStatsBackfillMode.Missing.toString,
            reason = Some("missing-only-backfill"),
            limit = 10
          )
        )
      )
      assertEquals(recomputeResponse.statusCode(), 202)
      val tasks = read[Vector[AdvancedStatsRecomputeTask]](recomputeResponse.body())
      assert(tasks.exists(_.owner == DashboardOwner.Player(playerB.id)))
      assert(!tasks.exists(_.owner == DashboardOwner.Player(playerA.id)))

      val summaryResponse = get(
        s"$baseUrl/admin/advanced-stats/summary?operatorId=${rootAdmin.id.value}"
      )
      assertEquals(summaryResponse.statusCode(), 200)
      val summary = read[AdvancedStatsTaskQueueSummary](summaryResponse.body())
      assert(summary.runnablePendingCount + summary.processingCount + summary.completedCount > 0)
      assertEquals(summary.deadLetterCount, 0)
    }
  }

  test("admin performance summary exposes normalized request and repository metrics") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:15:00Z")

    val root = app.playerService.registerPlayer("perf-root", "PerfRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val admin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))
    val owner = app.playerService.registerPlayer("perf-owner", "PerfOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650)
    val member = app.playerService.registerPlayer("perf-member", "PerfMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val club = app.clubService.createClub("Perf Club", owner.id, now, owner.asPrincipal)
    app.clubService.addMember(club.id, member.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val playersResponse = get(s"$baseUrl/players?clubId=${club.id.value}")
      assertEquals(playersResponse.statusCode(), 200)

      val clubDetailResponse = get(s"$baseUrl/public/clubs/${club.id.value}")
      assertEquals(clubDetailResponse.statusCode(), 200)

      val leaderboardResponse = get(s"$baseUrl/public/leaderboards/players")
      assertEquals(leaderboardResponse.statusCode(), 200)

      val summaryResponse = get(
        s"$baseUrl/admin/performance/summary?operatorId=${admin.id.value}&limit=20"
      )
      assertEquals(summaryResponse.statusCode(), 200)

      val summary = read[PerformanceDiagnosticsSnapshot](summaryResponse.body())
      assert(summary.totalRequestCount >= 2)
      assert(summary.totalRepositoryCallCount > 0)
      assert(summary.busiestRequests.exists(_.key == "GET /players"))
      assert(summary.busiestRequests.exists(_.key == "GET /public/clubs/:clubId"))
      assert(summary.slowestRequests.forall(!_.key.contains(club.id.value)))
      assert(summary.busiestRepositoryCalls.exists(entry =>
        entry.key.startsWith("PlayerRepository.") ||
          entry.key.startsWith("ClubRepository.") ||
          entry.key.startsWith("GlobalDictionaryRepository.") ||
          entry.key.startsWith("TournamentRepository.") ||
          entry.key.startsWith("MatchRecordRepository.")
      ))
      assert(summary.busiestRepositoryCalls.exists(_.key == "GlobalDictionaryRepository.findAll"))
    }
  }

  test("admin dictionary rejects unknown reserved runtime namespace keys") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T15:20:00Z")

    val root = app.playerService.registerPlayer("runtime-root", "RuntimeRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val admin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))

    withServer(app) { baseUrl =>
      val response = postJson(
        s"$baseUrl/admin/dictionary",
        write(
          UpsertDictionaryRequest(
            operatorId = admin.id.value,
            key = "rating.elo.experimentalFactor",
            value = "72",
            note = Some("should fail until registered")
          )
        )
      )
      assertEquals(response.statusCode(), 400)
      assert(response.body().contains("reserved runtime namespace"))
    }
  }

  test("dictionary namespace workflow allows approved owners to write metadata keys") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T15:30:00Z")

    val root = app.playerService.registerPlayer("ns-root", "NsRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = app.playerService.registerPlayer("ns-owner", "NsOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val transferee = app.playerService.registerPlayer("ns-transferee", "NsTransferee", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val superAdmin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))

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

  test("dictionary namespace collaborators can write and reminders can be processed over the API") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T16:35:00Z")
    val requestNow = Instant.now()

    val root = app.playerService.registerPlayer("ns-collab-root", "NsCollabRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = app.playerService.registerPlayer("ns-collab-owner", "NsCollabOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val coOwner = app.playerService.registerPlayer("ns-collab-coowner", "NsCollabCoOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1590)
    val editor = app.playerService.registerPlayer("ns-collab-editor", "NsCollabEditor", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val replacementEditor = app.playerService.registerPlayer("ns-collab-replacement", "NsCollabReplacement", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1570)
    val superAdmin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))

    withServer(app) { baseUrl =>
      val requestResponse = postJson(
        s"$baseUrl/dictionary/namespaces",
        write(
          RequestDictionaryNamespaceRequest(
            operatorId = owner.id.value,
            namespacePrefix = "ui.banner",
            coOwnerPlayerIds = Vector(coOwner.id.value),
            editorPlayerIds = Vector(editor.id.value),
            note = Some("frontend banners")
          )
        )
      )
      assertEquals(requestResponse.statusCode(), 201)
      val pending = read[DictionaryNamespaceRegistration](requestResponse.body())
      assertEquals(pending.coOwnerPlayerIds, Vector(coOwner.id))
      assertEquals(pending.editorPlayerIds, Vector(editor.id))

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

      val editorWrite = postJson(
        s"$baseUrl/admin/dictionary",
        write(
          UpsertDictionaryRequest(
            operatorId = editor.id.value,
            key = "ui.banner.message",
            value = "editor managed copy",
            note = Some("editor write")
          )
        )
      )
      assertEquals(editorWrite.statusCode(), 201)

      val collaboratorUpdate = postJson(
        s"$baseUrl/dictionary/namespaces/collaborators",
        write(
          UpdateDictionaryNamespaceCollaboratorsRequest(
            operatorId = coOwner.id.value,
            namespacePrefix = "ui.banner",
            coOwnerPlayerIds = Vector(coOwner.id.value),
            editorPlayerIds = Vector(replacementEditor.id.value),
            note = Some("rotate editor")
          )
        )
      )
      assertEquals(collaboratorUpdate.statusCode(), 200)
      val updated = read[DictionaryNamespaceRegistration](collaboratorUpdate.body())
      assertEquals(updated.editorPlayerIds, Vector(replacementEditor.id))

      val oldEditorDenied = postJson(
        s"$baseUrl/admin/dictionary",
        write(
          UpsertDictionaryRequest(
            operatorId = editor.id.value,
            key = "ui.banner.message",
            value = "old editor blocked",
            note = Some("should fail")
          )
        )
      )
      assertEquals(oldEditorDenied.statusCode(), 400)

      val reminderRequest = postJson(
        s"$baseUrl/dictionary/namespaces",
        write(
          RequestDictionaryNamespaceRequest(
            operatorId = owner.id.value,
            namespacePrefix = "ui.alert",
            note = Some("due-soon reminder"),
            reviewDueAt = Some(requestNow.plusSeconds(3600).toString)
          )
        )
      )
      assertEquals(reminderRequest.statusCode(), 201)

      val reminderProcess = postJson(
        s"$baseUrl/dictionary/namespaces/reminders/process",
        write(
          ProcessDictionaryNamespaceRemindersRequest(
            operatorId = superAdmin.id.value,
            asOf = Some(requestNow.plusSeconds(1800).toString),
            dueSoonHours = 2,
            reminderIntervalHours = 12,
            escalationGraceHours = 72
          )
        )
      )
      assertEquals(reminderProcess.statusCode(), 200)
      val reminderActions = read[Vector[DictionaryNamespaceReminderAction]](reminderProcess.body())
      assert(reminderActions.exists(action => action.namespacePrefix == "ui.alert." && action.reminderKind == DictionaryNamespaceReminderKind.DueSoon))
    }
  }

  test("dictionary namespace context club endpoints enforce explicit club scoping over the API") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T16:37:00Z")

    val root = app.playerService.registerPlayer("ns-context-root", "NsContextRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = app.playerService.registerPlayer("ns-context-owner", "NsContextOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val coOwner = app.playerService.registerPlayer("ns-context-coowner", "NsContextCoOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1590)
    val editor = app.playerService.registerPlayer("ns-context-editor", "NsContextEditor", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val outsider = app.playerService.registerPlayer("ns-context-outsider", "NsContextOutsider", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1570)
    val superAdmin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))

    val club = app.clubService.createClub("API Namespace Context Club", owner.id, now, owner.asPrincipal)
    app.clubService.addMember(club.id, coOwner.id, principalFor(app, owner.id))
    app.clubService.addMember(club.id, editor.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val requestResponse = postJson(
        s"$baseUrl/dictionary/namespaces",
        write(
          RequestDictionaryNamespaceRequest(
            operatorId = owner.id.value,
            namespacePrefix = "ui.banner",
            contextClubId = Some(club.id.value),
            coOwnerPlayerIds = Vector(coOwner.id.value),
            editorPlayerIds = Vector(editor.id.value),
            note = Some("club-scoped metadata")
          )
        )
      )
      assertEquals(requestResponse.statusCode(), 201)
      val pending = read[DictionaryNamespaceRegistration](requestResponse.body())
      assertEquals(pending.contextClubId, Some(club.id))

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

      val filteredList = get(
        s"$baseUrl/dictionary/namespaces?operatorId=${superAdmin.id.value}&status=Approved&contextClubId=${club.id.value}"
      )
      assertEquals(filteredList.statusCode(), 200)
      val filteredPage = readPage[DictionaryNamespaceRegistration](filteredList.body())
      assertEquals(filteredPage.total, 1)
      assertEquals(filteredPage.items.head.contextClubId, Some(club.id))

      val invalidCollaborators = postJson(
        s"$baseUrl/dictionary/namespaces/collaborators",
        write(
          UpdateDictionaryNamespaceCollaboratorsRequest(
            operatorId = owner.id.value,
            namespacePrefix = "ui.banner",
            coOwnerPlayerIds = Vector(coOwner.id.value),
            editorPlayerIds = Vector(outsider.id.value),
            note = Some("should fail")
          )
        )
      )
      assertEquals(invalidCollaborators.statusCode(), 400)
      assert(invalidCollaborators.body().contains(club.id.value))

      val transferDenied = postJson(
        s"$baseUrl/dictionary/namespaces/transfer",
        write(
          TransferDictionaryNamespaceRequest(
            operatorId = superAdmin.id.value,
            namespacePrefix = "ui.banner",
            newOwnerPlayerId = outsider.id.value,
            note = Some("should fail")
          )
        )
      )
      assertEquals(transferDenied.statusCode(), 400)
      assert(transferDenied.body().contains(club.id.value))

      val clearContext = postJson(
        s"$baseUrl/dictionary/namespaces/context",
        write(
          UpdateDictionaryNamespaceContextRequest(
            operatorId = owner.id.value,
            namespacePrefix = "ui.banner",
            contextClubId = None,
            note = Some("detach team context")
          )
        )
      )
      assertEquals(clearContext.statusCode(), 200)
      val cleared = read[DictionaryNamespaceRegistration](clearContext.body())
      assertEquals(cleared.contextClubId, None)

      val transferResponse = postJson(
        s"$baseUrl/dictionary/namespaces/transfer",
        write(
          TransferDictionaryNamespaceRequest(
            operatorId = superAdmin.id.value,
            namespacePrefix = "ui.banner",
            newOwnerPlayerId = outsider.id.value,
            note = Some("handoff after detach")
          )
        )
      )
      assertEquals(transferResponse.statusCode(), 200)
    }
  }

  test("dictionary namespace transfer rejects suspended owners over the API") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T16:40:00Z")

    val root = app.playerService.registerPlayer("ns-transfer-root", "NsTransferRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = app.playerService.registerPlayer("ns-transfer-owner", "NsTransferOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val suspended = app.playerService.registerPlayer("ns-transfer-suspended", "NsTransferSuspended", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val superAdmin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))
    val suspendedMember = app.playerRepository.findById(suspended.id).getOrElse(fail("suspended member missing"))
    app.playerRepository.save(suspendedMember.copy(status = PlayerStatus.Suspended))

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

    val root = app.playerService.registerPlayer("ns-backlog-root", "NsBacklogRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val ownerA = app.playerService.registerPlayer("ns-backlog-owner-a", "NsBacklogOwnerA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val ownerB = app.playerService.registerPlayer("ns-backlog-owner-b", "NsBacklogOwnerB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val superAdmin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))

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

  test("dictionary key and audit aggregate endpoints return targeted admin data") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:00:00Z")

    val root = app.playerService.registerPlayer("root-1", "Root", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val viewer = app.playerService.registerPlayer("root-2", "Viewer", RankSnapshot(RankPlatform.Custom, "A"), now, 1700)
    val admin = app.playerRepository.save(root.grantRole(RoleGrant.superAdmin(now)))
    val adminPrincipal = principalFor(app, admin.id)

    app.superAdminService.requestDictionaryNamespace(
      namespacePrefix = "rank.formula",
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      requestedAt = now
    )
    app.superAdminService.reviewDictionaryNamespace(
      namespacePrefix = "rank.formula",
      approve = true,
      actor = adminPrincipal,
      note = Some("bootstrap metadata namespace"),
      reviewedAt = now.plusSeconds(1)
    )

    val dictionaryEntry = app.superAdminService.upsertDictionary(
      key = "rank.formula.current",
      value = "uma+oka-v2",
      actor = adminPrincipal,
      note = Some("season 2"),
      updatedAt = now.plusSeconds(10)
    )

    withServer(app) { baseUrl =>
      val dictionaryResponse = get(s"$baseUrl/dictionary/${dictionaryEntry.key}")
      assertEquals(dictionaryResponse.statusCode(), 200)
      val storedEntry = read[GlobalDictionaryEntry](dictionaryResponse.body())
      assertEquals(storedEntry.value, dictionaryEntry.value)

      val forbiddenAudit = get(
        s"$baseUrl/audits/dictionary/${dictionaryEntry.key}?operatorId=${viewer.id.value}"
      )
      assertEquals(forbiddenAudit.statusCode(), 403)

      val auditResponse = get(
        s"$baseUrl/audits/dictionary/${dictionaryEntry.key}?operatorId=${admin.id.value}&eventType=GlobalDictionaryUpserted"
      )
      assertEquals(auditResponse.statusCode(), 200)
      val auditEntries = readPage[AuditEventEntry](auditResponse.body())
      assertEquals(auditEntries.total, 1)
      assertEquals(auditEntries.items.head.aggregateId, dictionaryEntry.key)
      assertEquals(auditEntries.items.head.eventType, "GlobalDictionaryUpserted")
    }
  }

