package riichinexus.microservices.club.domain

import java.sql.Connection
import java.time.Instant

import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.dictionary.domain.RuntimeDictionary
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.player.tables.player.PlayerTable

object ClubProjectionRefresher:
  def ensurePlayerDashboard(module: ClubModuleContext, playerId: PlayerId, at: Instant): Unit =
    val owner = DashboardOwner.Player(playerId)
    val dashboardRepository = module.dashboardRepository
    if dashboardRepository.findByOwner(owner).isEmpty then
      dashboardRepository.save(Dashboard.empty(owner, at))

  def refreshClubProjection(connection: Connection, module: ClubModuleContext, club: Club, at: Instant): Club =
    val refreshedClub = club.updatePowerRating(
      RuntimeDictionary.calculateClubPowerRating(club, findPlayer(connection), module.globalDictionaryRepository)
    )
    module.dashboardRepository.save(buildClubDashboard(connection, module, refreshedClub, at))
    refreshedClub

  private def buildClubDashboard(connection: Connection, module: ClubModuleContext, club: Club, at: Instant): Dashboard =
    val existingVersion = module.dashboardRepository.findByOwner(DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    val memberDashboards = club.members.flatMap { playerId =>
      findPlayer(connection)(playerId)
        .filter(_.status == PlayerStatus.Active)
        .flatMap(_ => module.dashboardRepository.findByOwner(DashboardOwner.Player(playerId)))
    }

    if memberDashboards.isEmpty then Dashboard.empty(DashboardOwner.Club(club.id), at).copy(version = existingVersion)
    else
      Dashboard(
        owner = DashboardOwner.Club(club.id),
        sampleSize = memberDashboards.map(_.sampleSize).sum,
        dealInRate = weightedAverage(memberDashboards, _.dealInRate),
        winRate = weightedAverage(memberDashboards, _.winRate),
        averageWinPoints = weightedAverage(memberDashboards, _.averageWinPoints),
        riichiRate = weightedAverage(memberDashboards, _.riichiRate),
        averagePlacement = weightedAverage(memberDashboards, _.averagePlacement),
        topFinishRate = weightedAverage(memberDashboards, _.topFinishRate),
        lastUpdatedAt = at,
        version = existingVersion
      )

  private def findPlayer(connection: Connection)(playerId: PlayerId): Option[Player] =
    PlayerTable.findById(connection, playerId)

  private def weightedAverage(dashboards: Vector[Dashboard], selector: Dashboard => Double): Double =
    val totalWeight = dashboards.map(_.sampleSize).sum
    if totalWeight <= 0 then 0.0
    else BigDecimal(dashboards.map(dashboard => selector(dashboard) * dashboard.sampleSize).sum / totalWeight.toDouble)
      .setScale(2, BigDecimal.RoundingMode.HALF_UP)
      .toDouble
