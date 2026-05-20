package riichinexus.microservices.player.domain

import java.time.Instant

import riichinexus.application.ports.*
import riichinexus.domain.model.*

private object PlayerProjectionSupport:
  def ensurePlayerDashboard(
      playerId: PlayerId,
      dashboardRepository: DashboardRepository,
      at: Instant
  ): Unit =
    val owner = DashboardOwner.Player(playerId)
    if dashboardRepository.findByOwner(owner).isEmpty then
      dashboardRepository.save(Dashboard.empty(owner, at))

final class PlayerRegistrationOperations(
    playerRepository: PlayerRepository,
    dashboardRepository: DashboardRepository,
    transactionManager: TransactionManager = NoOpTransactionManager
):
  def registerPlayer(
      userId: String,
      nickname: String,
      rank: RankSnapshot,
      registeredAt: Instant = Instant.now(),
      initialElo: Int = 1500
  ): Player =
    transactionManager.inTransaction {
      val player = playerRepository.findByUserId(userId) match
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

      val savedPlayer = playerRepository.save(player)
      PlayerProjectionSupport.ensurePlayerDashboard(savedPlayer.id, dashboardRepository, registeredAt)
      savedPlayer
    }
