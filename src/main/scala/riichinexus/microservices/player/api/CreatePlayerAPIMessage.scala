package riichinexus.microservices.player.api

import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.auth.objects.authorization.`private`.RoleGrant
import riichinexus.microservices.auth.objects.authorization.Role
import riichinexus.microservices.opsanalytics.api.`private`.EnsurePlayerDashboardPrivateAPIMessage
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.objects.{RankPlatform, RankSnapshot}
import riichinexus.microservices.player.objects.apiTypes.CreatePlayerRequest
import riichinexus.microservices.player.objects.{PlayerProfileView, PlayerRoleFlagsView}
import riichinexus.microservices.player.tables.players.PlayerTable
/** 创建玩家档案。 */
final case class CreatePlayerAPIMessage(
    request: CreatePlayerRequest
) extends APIMessage[PlayerProfileView]:

  override def plan(context: ApiPlanContext): IO[PlayerProfileView] =
    for
      registeredAt <- IO.realTimeInstant
      player <- resolvePlayerDraft(context, registeredAt)
      savedPlayer <- savePlayer(context, player)
      _ <- EnsurePlayerDashboardPrivateAPIMessage(savedPlayer.id, registeredAt).plan(context)
    yield playerProfileView(savedPlayer)

  private def rankSnapshot: RankSnapshot =
    RankSnapshot(RankPlatform.valueOf(request.rankPlatform), request.tier, request.stars)

  private def resolvePlayerDraft(context: ApiPlanContext, registeredAt: Instant): IO[Player] =
    IO.blocking {
      PlayerTable.findByUserId(context.connection, request.userId) match
        case Some(existing) =>
          existing.copy(
            nickname = request.nickname,
            currentRank = rankSnapshot
          )
        case None =>
          Player(
            id = PlayerIdGenerator.playerId(),
            userId = request.userId,
            nickname = request.nickname,
            registeredAt = registeredAt,
            currentRank = rankSnapshot,
            elo = request.initialElo,
            roleGrants = Vector(RoleGrant(Role.RegisteredPlayer, grantedAt = registeredAt))
          )
    }

  private def savePlayer(context: ApiPlanContext, player: Player): IO[Player] =
    IO.blocking(PlayerTable.save(context.connection, player))

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
