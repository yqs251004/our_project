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
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.DictionaryResponses.given
import riichinexus.microservices.opsanalytics.api.{
  OpsAnalyticsAdvancedStatsSummaryAPIMessage,
  OpsAnalyticsClubAdvancedStatsAPIMessage,
  OpsAnalyticsClubDashboardAPIMessage,
  OpsAnalyticsListAdvancedStatsTasksAPIMessage,
  OpsAnalyticsPlayerAdvancedStatsAPIMessage,
  OpsAnalyticsPlayerDashboardAPIMessage,
  OpsAnalyticsProcessAdvancedStatsAPIMessage,
  OpsAnalyticsRecomputeAdvancedStatsAPIMessage
}
import riichinexus.microservices.opsanalytics.objects.apiTypes.PerformanceDiagnosticsSnapshot
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

class ApiServerDashboardStatsSuite extends FunSuite with ApiServerSuiteSupport:

  test("dashboard endpoints enforce RBAC and allow scoped access") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T11:00:00Z")

    val owner = playerService(app).registerPlayer("dash-1", "Owner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val intruder = playerService(app).registerPlayer("dash-2", "Intruder", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val member = playerService(app).registerPlayer("dash-3", "Member", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1520)

    val club = clubApi(app).createClub("Dashboard Club", owner.id, now, owner.asPrincipal)
    clubApi(app).addMember(club.id, member.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val forbiddenPlayer = postJson(
        s"$baseUrl/api/opsanalyticsplayerdashboardapi",
        write(OpsAnalyticsPlayerDashboardAPIMessage(owner.id, intruder.id))
      )
      assertEquals(forbiddenPlayer.statusCode(), 403)

      val ownPlayer = postJson(
        s"$baseUrl/api/opsanalyticsplayerdashboardapi",
        write(OpsAnalyticsPlayerDashboardAPIMessage(owner.id, owner.id))
      )
      assertEquals(ownPlayer.statusCode(), 200)

      val ownClub = postJson(
        s"$baseUrl/api/opsanalyticsclubdashboardapi",
        write(OpsAnalyticsClubDashboardAPIMessage(club.id, owner.id))
      )
      assertEquals(ownClub.statusCode(), 200)

      val forbiddenClub = postJson(
        s"$baseUrl/api/opsanalyticsclubdashboardapi",
        write(OpsAnalyticsClubDashboardAPIMessage(club.id, member.id))
      )
      assertEquals(forbiddenClub.statusCode(), 403)
    }
  }

  test("advanced stats endpoints expose dedicated boards with dashboard RBAC") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T11:45:00Z")

    val owner = playerService(app).registerPlayer("adv-owner", "AdvOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val root = playerService(app).registerPlayer("adv-root", "AdvRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val rootAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    val member = playerService(app).registerPlayer("adv-member", "AdvMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1520)
    val intruder = playerService(app).registerPlayer("adv-intruder", "AdvIntruder", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1490)
    val extraA = playerService(app).registerPlayer("adv-extra-a", "AdvExtraA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1510)
    val extraB = playerService(app).registerPlayer("adv-extra-b", "AdvExtraB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)

    val club = clubApi(app).createClub("Advanced Stats Club", owner.id, now, owner.asPrincipal)
    clubApi(app).addMember(club.id, member.id, principalFor(app, owner.id))
    clubApi(app).addMember(club.id, extraA.id, principalFor(app, owner.id))
    clubApi(app).addMember(club.id, extraB.id, principalFor(app, owner.id))

    val stage = TournamentStage(IdGenerator.stageId(), "Advanced Stats Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Advanced Stats Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )
    Vector(owner, member, extraA, extraB).foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id))
    tournamentService(app).publishTournament(tournament.id)

    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id).head
    tableService(app).startTable(table.id, now.plusSeconds(60))
    tableService(app).recordCompletedTable(
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
      val taskIndex = postJson(
        s"$baseUrl/api/opsanalyticslistadvancedstatstasksapi",
        write(OpsAnalyticsListAdvancedStatsTasksAPIMessage(rootAdmin.id))
      )
      assertEquals(taskIndex.statusCode(), 200)
      assert(readPage[AdvancedStatsRecomputeTask](taskIndex.body()).total >= 0)

      val recomputeTasks = postJson(
        s"$baseUrl/api/opsanalyticsrecomputeadvancedstatsapi",
        write(
          OpsAnalyticsRecomputeAdvancedStatsAPIMessage(
            operatorId = rootAdmin.id,
            ownerType = Some("player"),
            ownerId = Some(owner.id.value),
            reason = Some("api-test-backfill")
          )
        )
      )
      assertEquals(recomputeTasks.statusCode(), 202)
      assert(read[Vector[AdvancedStatsRecomputeTask]](recomputeTasks.body()).nonEmpty)

      val taskIndexAfter = postJson(
        s"$baseUrl/api/opsanalyticslistadvancedstatstasksapi",
        write(OpsAnalyticsListAdvancedStatsTasksAPIMessage(rootAdmin.id))
      )
      assertEquals(taskIndexAfter.statusCode(), 200)
      assert(readPage[AdvancedStatsRecomputeTask](taskIndexAfter.body()).total > 0)

      val processTasks = postJson(
        s"$baseUrl/api/opsanalyticsprocessadvancedstatsapi",
        write(OpsAnalyticsProcessAdvancedStatsAPIMessage(rootAdmin.id, 20))
      )
      assertEquals(processTasks.statusCode(), 200)

      val playerReadRecompute = postApi(
        baseUrl,
        OpsAnalyticsRecomputeAdvancedStatsAPIMessage(
          operatorId = rootAdmin.id,
          ownerType = Some("player"),
          ownerId = Some(owner.id.value),
          reason = Some("api-test-player-read")
        )
      )
      assertEquals(playerReadRecompute.statusCode(), 202)
      val clubReadRecompute = postApi(
        baseUrl,
        OpsAnalyticsRecomputeAdvancedStatsAPIMessage(
          operatorId = rootAdmin.id,
          ownerType = Some("club"),
          ownerId = Some(club.id.value),
          reason = Some("api-test-club-read")
        )
      )
      assertEquals(clubReadRecompute.statusCode(), 202)
      val processReadTasks = postApi(baseUrl, OpsAnalyticsProcessAdvancedStatsAPIMessage(rootAdmin.id, 20))
      assertEquals(processReadTasks.statusCode(), 200)

      val ownPlayerStats = postJson(
        s"$baseUrl/api/opsanalyticsplayeradvancedstatsapi",
        write(OpsAnalyticsPlayerAdvancedStatsAPIMessage(owner.id, owner.id))
      )
      assertEquals(ownPlayerStats.statusCode(), 200)
      val playerBoard = read[AdvancedStatsBoard](ownPlayerStats.body())
      assert(playerBoard.sampleSize > 0)

      val ownClubStats = postJson(
        s"$baseUrl/api/opsanalyticsclubadvancedstatsapi",
        write(OpsAnalyticsClubAdvancedStatsAPIMessage(club.id, owner.id))
      )
      assertEquals(ownClubStats.statusCode(), 200)
      val clubBoard = read[AdvancedStatsBoard](ownClubStats.body())
      assert(clubBoard.sampleSize > 0)

      val forbiddenPlayerStats = postJson(
        s"$baseUrl/api/opsanalyticsplayeradvancedstatsapi",
        write(OpsAnalyticsPlayerAdvancedStatsAPIMessage(owner.id, intruder.id))
      )
      assertEquals(forbiddenPlayerStats.statusCode(), 403)

      val forbiddenClubStats = postJson(
        s"$baseUrl/api/opsanalyticsclubadvancedstatsapi",
        write(OpsAnalyticsClubAdvancedStatsAPIMessage(club.id, member.id))
      )
      assertEquals(forbiddenClubStats.statusCode(), 403)
    }
  }

  test("advanced stats admin endpoints expose summary and missing-only backfill") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:05:00Z")

    val root = playerService(app).registerPlayer("adv-summary-root", "AdvSummaryRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2100)
    val rootAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    val playerA = playerService(app).registerPlayer("adv-summary-a", "AdvSummaryA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val playerB = playerService(app).registerPlayer("adv-summary-b", "AdvSummaryB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    advancedStatsBoardRepository(app).save(AdvancedStatsBoard.empty(DashboardOwner.Player(playerA.id), now))

    withServer(app) { baseUrl =>
      val recomputeResponse = postJson(
        s"$baseUrl/api/opsanalyticsrecomputeadvancedstatsapi",
        write(
          OpsAnalyticsRecomputeAdvancedStatsAPIMessage(
            operatorId = rootAdmin.id,
            mode = AdvancedStatsBackfillMode.Missing,
            reason = Some("missing-only-backfill"),
            limit = 10
          )
        )
      )
      assertEquals(recomputeResponse.statusCode(), 202)
      val tasks = read[Vector[AdvancedStatsRecomputeTask]](recomputeResponse.body())
      assert(tasks.exists(_.owner == DashboardOwner.Player(playerB.id)))
      assert(!tasks.exists(_.owner == DashboardOwner.Player(playerA.id)))

      val summaryResponse = postJson(
        s"$baseUrl/api/opsanalyticsadvancedstatssummaryapi",
        write(OpsAnalyticsAdvancedStatsSummaryAPIMessage(rootAdmin.id))
      )
      assertEquals(summaryResponse.statusCode(), 200)
      val summary = read[AdvancedStatsTaskQueueSummary](summaryResponse.body())
      assert(summary.runnablePendingCount + summary.processingCount + summary.completedCount > 0)
      assertEquals(summary.deadLetterCount, 0)
    }
  }
