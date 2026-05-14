package riichinexus.microservices.opsanalytics.api

import riichinexus.domain.model.*
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsTables

object DashboardApi:

  def playerDashboard(
      tables: OpsAnalyticsTables,
      playerId: PlayerId
  ): Option[Dashboard] =
    tables.findDashboard(DashboardOwner.Player(playerId))

  def clubDashboard(
      tables: OpsAnalyticsTables,
      clubId: ClubId
  ): Option[Dashboard] =
    tables.findDashboard(DashboardOwner.Club(clubId))

  def playerAdvancedStats(
      tables: OpsAnalyticsTables,
      playerId: PlayerId
  ): Option[AdvancedStatsBoard] =
    tables.findAdvancedStatsBoard(DashboardOwner.Player(playerId))

  def clubAdvancedStats(
      tables: OpsAnalyticsTables,
      clubId: ClubId
  ): Option[AdvancedStatsBoard] =
    tables.findAdvancedStatsBoard(DashboardOwner.Club(clubId))
