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

private[opsanalytics] trait DemoScenarioSnapshotSupport extends DemoScenarioSupport:
  protected def buildScenarioSnapshot(
      config: DemoScenarioConfig,
      recommendedOperatorId: PlayerId,
      tournament: Tournament,
      stage: TournamentStage
  ): DemoScenarioSnapshot =
    val tables = tableRepository.findByTournamentAndStage(tournament.id, stage.id)
      .sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))
    val matchRecordsByTableId = matchRecordRepository.findAll().map(record => record.tableId -> record).toMap
    val playerDashboardSummaryById = PlayerSeeds.flatMap(seed => playerRepository.findByUserId(seed.userId))
      .map(player => player.id -> dashboardRepository.findByOwner(DashboardOwner.Player(player.id)).map(toDemoDashboardSummary))
      .toMap
    val playerAdvancedStatsById = PlayerSeeds.flatMap(seed => playerRepository.findByUserId(seed.userId))
      .map(player => player.id -> advancedStatsBoardRepository.findByOwner(DashboardOwner.Player(player.id)).map(toDemoAdvancedStatsSummary))
      .toMap
    val demoPlayers = PlayerSeeds.flatMap(seed => playerRepository.findByUserId(seed.userId))
      .sortBy(player => (player.nickname, player.id.value))
      .map { player =>
        DemoScenarioPlayerView(
          playerId = player.id,
          userId = player.userId,
          nickname = player.nickname,
          currentRank = player.currentRank,
          elo = player.elo,
          status = player.status,
          clubIds = player.boundClubIds,
          isSuperAdmin = player.roleGrants.exists(_.role == RoleKind.SuperAdmin),
          isTournamentAdmin = player.roleGrants.exists(_.role == RoleKind.TournamentAdmin),
          isClubAdmin = player.roleGrants.exists(_.role == RoleKind.ClubAdmin),
          dashboard = playerDashboardSummaryById.getOrElse(player.id, None),
          advancedStats = playerAdvancedStatsById.getOrElse(player.id, None)
        )
      }
    val clubs = clubRepository.findAll()
      .filter(club => DemoClubNames.contains(club.name))
      .sortBy(_.name)
      .map { club =>
        val clubDashboard = dashboardRepository.findByOwner(DashboardOwner.Club(club.id)).map(toDemoDashboardSummary)
        val clubAdvancedStats = advancedStatsBoardRepository.findByOwner(DashboardOwner.Club(club.id)).map(toDemoAdvancedStatsSummary)
        DemoScenarioClubView(
          clubId = club.id,
          name = club.name,
          memberIds = club.members,
          adminIds = club.admins,
          powerRating = club.powerRating,
          totalPoints = club.totalPoints,
          treasuryBalance = club.treasuryBalance,
          pointPool = club.pointPool,
          honorTitles = club.honors.map(_.title).sorted,
          dashboard = clubDashboard,
          advancedStats = clubAdvancedStats
        )
      }
    val guestSession = guestSessionRepository.findAll()
      .find(_.displayName == config.guestDisplayName)
    val clubIds = clubs.map(_.clubId).toSet
    val playerIds = demoPlayers.map(_.playerId).toSet
    val publicSchedules = publicQueryService.publicSchedules()
      .filter(_.tournamentId == tournament.id)
    val publicClubDirectory = publicQueryService.publicClubDirectory()
      .filter(entry => clubIds.contains(entry.clubId))
      .sortBy(_.name)
    val playerLeaderboard = publicQueryService.publicPlayerLeaderboard(limit = math.max(20, playerIds.size))
      .filter(entry => playerIds.contains(entry.playerId))
    val clubLeaderboard = publicQueryService.publicClubLeaderboard(limit = math.max(10, clubIds.size))
      .filter(entry => clubIds.contains(entry.clubId))
    val expectedDashboardOwners =
      playerIds.map(DashboardOwner.Player.apply) ++ clubIds.map(DashboardOwner.Club.apply)
    val expectedAdvancedStatsOwners = expectedDashboardOwners
    val outboxRecords = domainEventOutboxRepository.findAll()
    val advancedStatsTasks = advancedStatsRecomputeTaskRepository.findAll()
    val tableViews = tables.map { table =>
      DemoScenarioTableView(
        tableId = table.id,
        tableNo = table.tableNo,
        stageRoundNumber = table.stageRoundNumber,
        status = table.status,
        startedAt = table.startedAt,
        endedAt = table.endedAt,
        hasMatchRecord = matchRecordsByTableId.contains(table.id),
        hasPaifu = table.paifuId.nonEmpty,
        hasAppeal = table.appealTicketIds.nonEmpty,
        seats = table.seats.map { seat =>
          val nickname = playerRepository.findById(seat.playerId).map(_.nickname).getOrElse(seat.playerId.value)
          DemoScenarioTableSeatView(
            seat = seat.seat,
            playerId = seat.playerId,
            nickname = nickname,
            clubId = seat.clubId,
            initialPoints = seat.initialPoints,
            ready = seat.ready,
            disconnected = seat.disconnected
          )
        }
      )
    }
    val recommendedRequests = Vector(
      DemoScenarioApiRequest(
        method = "GET",
        path = s"/demo/summary?variant=${config.variant.toString}&bootstrapIfMissing=true",
        description = "One-call bootstrap plus all demo cards, tables, public lists and readiness state"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = s"/demo/guide?variant=${config.variant.toString}&bootstrapIfMissing=true&refreshDerived=true",
        description = "Structured walkthrough for presenter notes and frontend section ordering"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = s"/demo/readiness?variant=${config.variant.toString}&bootstrapIfMissing=true&refreshDerived=true",
        description = "Read derived-view readiness without loading the entire scenario payload"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = s"/demo/widgets?variant=${config.variant.toString}&bootstrapIfMissing=true&refreshDerived=true",
        description = "Chart-friendly widget payload for the frontend dashboard and overview panels"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = s"/demo/actions?variant=${config.variant.toString}&bootstrapIfMissing=true&refreshDerived=true",
        description = "Variant-aware interactive demo actions for presenter buttons and scripted state changes"
      ),
      DemoScenarioApiRequest(
        method = "POST",
        path = s"/demo/reset?variant=${config.variant.toString}&refreshDerived=true",
        description = "Reset the selected demo variant back to its seeded baseline"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = "/public/schedules",
        description = "Public tournament schedule list for landing page and event overview"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = "/public/clubs",
        description = "Public club directory for club cards and relationship overview"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = "/public/leaderboards/players",
        description = "Public player leaderboard with ELO and normalized rank score"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = "/public/leaderboards/clubs",
        description = "Public club leaderboard for team ranking widgets"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = s"/tournaments/${tournament.id.value}/stages/${stage.id.value}/tables",
        description = "Stage table list with live status and round filters"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = s"/dashboards/players/${recommendedOperatorId.value}?operatorId=${recommendedOperatorId.value}",
        description = "Operator-scoped player dashboard detail for the recommended demo admin"
      ),
      DemoScenarioApiRequest(
        method = "GET",
        path = s"/advanced-stats/players/${recommendedOperatorId.value}?operatorId=${recommendedOperatorId.value}",
        description = "Operator-scoped advanced stats board for charts and deeper player analytics"
      )
    ) ++ (
      if config.variant == DemoScenarioVariant.Appeal then
        Vector(
          DemoScenarioApiRequest(
            method = "GET",
            path = "/appeals?status=Open&limit=20",
            description = "Appeal work queue for the appeal-focused demo variant"
          )
        )
      else Vector.empty
    ) ++ guestSession.toVector.map(session =>
        DemoScenarioApiRequest(
          method = "GET",
          path = s"/guest-sessions/${session.id.value}",
          description = "Guest session detail for anonymous-flow demos"
        )
      )

    val snapshot = DemoScenarioSnapshot(
      variant = config.variant,
      seededAt = SeededAt,
      guestSessionId = guestSession.map(_.id),
      recommendedOperatorId = recommendedOperatorId,
      players = demoPlayers,
      clubs = clubs,
      tournament = DemoScenarioTournamentView(
        tournamentId = tournament.id,
        name = tournament.name,
        status = tournament.status,
        stageId = stage.id,
        stageName = stage.name,
        tableIds = tables.map(_.id),
        archivedTableIds = tables.filter(_.status == TableStatus.Archived).map(_.id),
        tables = tableViews
      ),
      publicSchedules = publicSchedules,
      publicClubDirectory = publicClubDirectory,
      playerLeaderboard = playerLeaderboard,
      clubLeaderboard = clubLeaderboard,
      recommendedRequests = recommendedRequests,
      availableActions = Vector.empty,
      readiness = DemoScenarioReadiness(
        dashboardOwnersExpected = expectedDashboardOwners.size,
        dashboardOwnersReady = expectedDashboardOwners.count(owner => dashboardRepository.findByOwner(owner).nonEmpty),
        advancedStatsOwnersExpected = expectedAdvancedStatsOwners.size,
        advancedStatsOwnersReady = expectedAdvancedStatsOwners.count(owner => advancedStatsBoardRepository.findByOwner(owner).nonEmpty),
        pendingOutboxCount = outboxRecords.count(_.status == DomainEventOutboxStatus.Pending),
        deadLetterOutboxCount = outboxRecords.count(_.status == DomainEventOutboxStatus.DeadLetter),
        pendingAdvancedStatsTaskCount = advancedStatsTasks.count(task =>
          task.status == AdvancedStatsRecomputeTaskStatus.Pending || task.status == AdvancedStatsRecomputeTaskStatus.Processing
        ),
        deadLetterAdvancedStatsTaskCount = advancedStatsTasks.count(_.status == AdvancedStatsRecomputeTaskStatus.DeadLetter)
      )
    )
    snapshot.copy(availableActions = buildActionCatalog(snapshot))
