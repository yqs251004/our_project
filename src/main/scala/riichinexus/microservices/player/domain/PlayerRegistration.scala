package riichinexus.microservices.player.domain

import java.sql.Connection
import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.RoleGrant
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTable
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.player.tables.player.PlayerTable

object PlayerRegistration:
  def register(
      connection: Connection,
      userId: String,
      nickname: String,
      rank: RankSnapshot,
      registeredAt: Instant,
      initialElo: Int
  ): Player =
    val player = PlayerTable.findByUserId(connection, userId) match
      case Some(existing) =>
        existing.copy(
          nickname = nickname,
          currentRank = rank
        )
      case None =>
        Player(
          id = IdGenerator.playerId(),
          userId = userId,
          nickname = nickname,
          registeredAt = registeredAt,
          currentRank = rank,
          elo = initialElo,
          roleGrants = Vector(RoleGrant.registered(registeredAt))
        )

    val savedPlayer = PlayerTable.save(connection, player)
    ensurePlayerDashboard(connection, savedPlayer.id, registeredAt)
    savedPlayer

  private def ensurePlayerDashboard(
      connection: Connection,
      playerId: PlayerId,
      at: Instant
  ): Unit =
    val owner = DashboardOwner.Player(playerId)
    if DashboardTable.findByOwner(connection, owner).isEmpty then
      DashboardTable.save(connection, Dashboard.empty(owner, at))
