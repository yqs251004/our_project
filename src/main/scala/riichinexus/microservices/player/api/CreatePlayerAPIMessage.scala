package riichinexus.microservices.player.api

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import java.sql.Connection
import java.time.Instant

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.RoleGrant
import riichinexus.microservices.auth.domain.model.Role
import riichinexus.microservices.opsanalytics.api.`private`.EnsurePlayerDashboardAPIMessage
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.objects.{RankPlatform, RankSnapshot}
import riichinexus.microservices.player.objects.apiTypes.CreatePlayerRequest
import riichinexus.microservices.player.objects.apiTypes.{PlayerProfileView, PlayerRoleFlagsView}
import riichinexus.microservices.player.tables.players.PlayerTable
import upickle.default.*

final case class CreatePlayerAPIMessage(
    request: CreatePlayerRequest
) extends APIMessage[PlayerProfileView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlayerProfileView] =
    for
      registeredAt <- IO.realTimeInstant
      player <- IO.blocking {
        CreatePlayerAPIMessage.createPlayer(
          connection = context.connection,
          userId = request.userId,
          nickname = request.nickname,
          rank = rankSnapshot,
          registeredAt = registeredAt,
          initialElo = request.initialElo
        )
      }
    yield playerProfileView(player)

  private def rankSnapshot: RankSnapshot =
    RankSnapshot(RankPlatform.valueOf(request.rankPlatform), request.tier, request.stars)

  private def playerProfileView(player: Player): PlayerProfileView =
    PlayerProfileView(
      playerId = player.id.value,
      userId = player.userId,
      nickname = player.nickname,
      registeredAt = player.registeredAt.toString,
      currentRank = player.currentRank,
      elo = player.elo,
      clubId = player.clubId.map(_.value),
      affiliatedClubIds = player.affiliatedClubIds.map(_.value),
      status = player.status.toString,
      roles = PlayerRoleFlagsView(
        isRegisteredPlayer = PlayerRoleFunctions.effectiveRoleGrants(player).exists(_.role == Role.RegisteredPlayer),
        isClubAdmin = player.roleGrants.exists(_.role == Role.ClubAdmin),
        isTournamentAdmin = player.roleGrants.exists(_.role == Role.TournamentAdmin),
        isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
      ),
      bannedReason = player.bannedReason
    )

object CreatePlayerAPIMessage:

  def createPlayer(
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
          roleGrants = Vector(RoleGrantFunctions.registered(registeredAt))
        )

    val savedPlayer = persistPlayer(connection, player)
    ensurePlayerDashboard(connection, savedPlayer.id, registeredAt)
    savedPlayer

  def persistPlayer(connection: Connection, player: Player): Player =
    PlayerTable.save(connection, player)

  def findPlayerByUserId(connection: Connection, userId: String): Option[Player] =
    PlayerTable.findByUserId(connection, userId)

  private def ensurePlayerDashboard(
      connection: Connection,
      playerId: PlayerId,
      at: Instant
  ): Unit =
    EnsurePlayerDashboardAPIMessage(playerId, at)
      .plan(apiContext(connection))
      .unsafeRunSync()

  private def apiContext(connection: Connection): ApiPlanContext =
    ApiPlanContext(support = null, bearerToken = None, connection = connection)
