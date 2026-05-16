package riichinexus.microservices.opsanalytics.api

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.microservices.auth.api.GuestSessionApplicationService
import riichinexus.microservices.club.api.ClubApplicationService
import riichinexus.microservices.dictionary.api.RuntimeDictionarySupport
import riichinexus.microservices.opsanalytics.api.responses.*
import riichinexus.microservices.player.api.PlayerApplicationService
import riichinexus.microservices.publicquery.api.PublicQueryService
import riichinexus.microservices.tournament.api.{TableLifecycleService, TournamentApplicationService}
import riichinexus.microservices.tournament.appeal.api.AppealApplicationService

private[opsanalytics] trait DemoScenarioGuideSupport extends DemoScenarioSupport:
  protected def buildGuide(snapshot: DemoScenarioSnapshot): DemoScenarioGuide =
    val summaryRequest = snapshot.recommendedRequests.find(_.path.startsWith("/demo/summary"))
    val widgetsRequest = snapshot.recommendedRequests.find(_.path.startsWith("/demo/widgets"))
    val actionsRequest = snapshot.recommendedRequests.find(_.path.startsWith("/demo/actions"))
    val schedulesRequest = snapshot.recommendedRequests.find(_.path == "/public/schedules")
    val clubsRequest = snapshot.recommendedRequests.find(_.path == "/public/clubs")
    val playerLeaderboardRequest = snapshot.recommendedRequests.find(_.path == "/public/leaderboards/players")
    val tableRequest = snapshot.recommendedRequests.find(_.path.contains("/tables"))

    DemoScenarioGuide(
      variant = snapshot.variant,
      title = "RiichiNexus Demo Walkthrough",
      summary = "Use the demo summary as the single bootstrap source, then drill into public schedules, clubs, leaderboards, and table state for the frontend presentation.",
      steps = Vector(
        DemoScenarioGuideStep(
          title = "Bootstrap the scenario",
          description = "Seed demo players, clubs, tournament data, and derived views in one call.",
          request = summaryRequest
        ),
        DemoScenarioGuideStep(
          title = "Render the player and club cards",
          description = "Use the embedded `players` and `clubs` arrays from the summary for the first screen.",
          request = summaryRequest
        ),
        DemoScenarioGuideStep(
          title = "Show the public tournament widgets",
          description = "Use the widget payload together with schedules and leaderboards to demonstrate the read-only visitor experience.",
          request = widgetsRequest.orElse(schedulesRequest).orElse(playerLeaderboardRequest)
        ),
        DemoScenarioGuideStep(
          title = "Open the detailed table area",
          description = "Use the seeded stage tables to show round number, seat allocation, and archived-table state.",
          request = tableRequest
        ),
        DemoScenarioGuideStep(
          title = "Demonstrate club browsing",
          description = "Use the public club directory for club cards, honors, and rivalry/alliance context.",
          request = clubsRequest
        ),
        DemoScenarioGuideStep(
          title = "Trigger interactive demo actions",
          description = "Use the action catalog to archive the next table, create an appeal, or reset the scenario during the live demo.",
          request = actionsRequest
        )
      ),
      frontendSections = Vector(
        "hero-summary",
        "player-cards",
        "club-cards",
        "table-grid",
        "leaderboard-strip",
        "public-schedule-panel",
        "demo-action-bar"
      ),
      presenterNotes = Vector(
        s"Recommended operator id: ${snapshot.recommendedOperatorId.value}",
        s"Guest session available: ${snapshot.guestSessionId.map(_.value).getOrElse("none")}",
        s"Archived demo tables: ${snapshot.tournament.archivedTableIds.size}",
        s"Available interactive demo actions: ${snapshot.availableActions.count(_.enabled)}/${snapshot.availableActions.size}",
        s"Readiness pending outbox count: ${snapshot.readiness.pendingOutboxCount}",
        s"Readiness pending advanced-stats tasks: ${snapshot.readiness.pendingAdvancedStatsTaskCount}"
      )
    )

  protected def buildWidgets(snapshot: DemoScenarioSnapshot): DemoScenarioWidgets =
    val headlineMetrics = Vector(
      DemoScenarioWidgetMetric(
        key = "players",
        label = "Players",
        value = snapshot.players.size.toDouble,
        formattedValue = snapshot.players.size.toString
      ),
      DemoScenarioWidgetMetric(
        key = "clubs",
        label = "Clubs",
        value = snapshot.clubs.size.toDouble,
        formattedValue = snapshot.clubs.size.toString
      ),
      DemoScenarioWidgetMetric(
        key = "tables",
        label = "Tables",
        value = snapshot.tournament.tables.size.toDouble,
        formattedValue = snapshot.tournament.tables.size.toString
      ),
      DemoScenarioWidgetMetric(
        key = "archivedTables",
        label = "Archived Tables",
        value = snapshot.tournament.archivedTableIds.size.toDouble,
        formattedValue = snapshot.tournament.archivedTableIds.size.toString
      )
    )

    val playerEloSeries = snapshot.players
      .sortBy(player => (-player.elo, player.nickname))
      .map(player =>
        DemoScenarioWidgetMetric(
          key = player.playerId.value,
          label = player.nickname,
          value = player.elo.toDouble,
          formattedValue = player.elo.toString
        )
      )

    val clubPowerSeries = snapshot.clubs
      .sortBy(club => (-club.powerRating, club.name))
      .map(club =>
        DemoScenarioWidgetMetric(
          key = club.clubId.value,
          label = club.name,
          value = club.powerRating,
          formattedValue = f"${club.powerRating}%.2f"
        )
      )

    val playerLeaderboardPreview = snapshot.playerLeaderboard
      .take(5)
      .map(entry =>
        DemoScenarioWidgetMetric(
          key = entry.playerId.value,
          label = entry.nickname,
          value = entry.elo.toDouble,
          formattedValue = s"ELO ${entry.elo}"
        )
      )

    val clubLeaderboardPreview = snapshot.clubLeaderboard
      .take(5)
      .map(entry =>
        DemoScenarioWidgetMetric(
          key = entry.clubId.value,
          label = entry.name,
          value = entry.powerRating,
          formattedValue = f"${entry.powerRating}%.2f"
        )
      )

    val tableStatusBreakdown = snapshot.tournament.tables
      .groupBy(_.status)
      .toVector
      .sortBy(_._1.toString)
      .map { case (status, tables) =>
        DemoScenarioWidgetCount(
          key = status.toString,
          label = status.toString,
          count = tables.size
        )
      }

    val readinessBreakdown = Vector(
      DemoScenarioWidgetCount(
        key = "dashboardReady",
        label = "Dashboard Ready",
        count = snapshot.readiness.dashboardOwnersReady
      ),
      DemoScenarioWidgetCount(
        key = "dashboardExpected",
        label = "Dashboard Expected",
        count = snapshot.readiness.dashboardOwnersExpected
      ),
      DemoScenarioWidgetCount(
        key = "advancedStatsReady",
        label = "Advanced Stats Ready",
        count = snapshot.readiness.advancedStatsOwnersReady
      ),
      DemoScenarioWidgetCount(
        key = "advancedStatsExpected",
        label = "Advanced Stats Expected",
        count = snapshot.readiness.advancedStatsOwnersExpected
      ),
      DemoScenarioWidgetCount(
        key = "pendingOutbox",
        label = "Pending Outbox",
        count = snapshot.readiness.pendingOutboxCount
      ),
      DemoScenarioWidgetCount(
        key = "pendingAdvancedStatsTasks",
        label = "Pending Advanced Stats Tasks",
        count = snapshot.readiness.pendingAdvancedStatsTaskCount
      )
    )

    DemoScenarioWidgets(
      variant = snapshot.variant,
      generatedAt = Instant.now(),
      headlineMetrics = headlineMetrics,
      playerEloSeries = playerEloSeries,
      clubPowerSeries = clubPowerSeries,
      playerLeaderboardPreview = playerLeaderboardPreview,
      clubLeaderboardPreview = clubLeaderboardPreview,
      tableStatusBreakdown = tableStatusBreakdown,
      readinessBreakdown = readinessBreakdown
    )

  protected def buildActionCatalog(
      snapshot: DemoScenarioSnapshot
  ): Vector[DemoScenarioActionSpec] =
    val archiveEnabled = snapshot.tournament.tables.exists(table =>
      table.status == TableStatus.WaitingPreparation || table.status == TableStatus.InProgress
    )
    val activeAppealCount = snapshot.tournament.tables.count(_.hasAppeal)
    val canCreateAppeal =
      snapshot.tournament.tables.exists(table =>
        table.status != TableStatus.Archived && !table.hasAppeal
      )
    val canResolveAppeal = activeAppealCount > 0

    Vector(
      DemoScenarioActionSpec(
        code = DemoScenarioActionCode.RefreshScenario,
        label = "Refresh",
        description = "Recompute derived demo views without reseeding data.",
        method = "POST",
        path = s"/demo/actions/RefreshScenario?variant=${snapshot.variant.toString}",
        enabled = true
      ),
      DemoScenarioActionSpec(
        code = DemoScenarioActionCode.ResetScenario,
        label = "Reset",
        description = "Replay the seeded baseline for the selected demo variant.",
        method = "POST",
        path = s"/demo/actions/ResetScenario?variant=${snapshot.variant.toString}",
        enabled = true
      ),
      DemoScenarioActionSpec(
        code = DemoScenarioActionCode.ArchiveNextTable,
        label = "Archive Next Table",
        description = "Move the next runnable table through start/archive for demo progression.",
        method = "POST",
        path = s"/demo/actions/ArchiveNextTable?variant=${snapshot.variant.toString}",
        enabled = archiveEnabled
      ),
      DemoScenarioActionSpec(
        code = DemoScenarioActionCode.FileOpenAppeal,
        label = "Create Appeal",
        description = "Create a moderator-facing appeal on the next eligible non-archived table.",
        method = "POST",
        path = s"/demo/actions/FileOpenAppeal?variant=${snapshot.variant.toString}",
        enabled = canCreateAppeal
      ),
      DemoScenarioActionSpec(
        code = DemoScenarioActionCode.ResolveOldestAppeal,
        label = "Resolve Appeal",
        description = "Resolve the oldest active appeal in the selected demo variant.",
        method = "POST",
        path = s"/demo/actions/ResolveOldestAppeal?variant=${snapshot.variant.toString}",
        enabled = canResolveAppeal
      )
    )
