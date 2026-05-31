package riichinexus.microservices.player.api

import java.sql.Connection
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.PlayerId
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.player.objects.Player
import riichinexus.microservices.player.objects.apiTypes.{PlayerProfileView, PlayerRoleFlagsView}
import riichinexus.microservices.player.tables.players.PlayerTable
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.apiTypes.RankSnapshotView
import upickle.default.*

final case class GetPlayerAPIMessage(
    playerId: String
) extends APIMessage[PlayerProfileView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlayerProfileView] =
    for
      id <- IO.blocking(PlayerId(playerId))
      player <- IO.blocking(findPlayer(context.connection, id))
    yield playerProfileView(player)

  private def findPlayer(connection: java.sql.Connection, playerId: PlayerId) =
    GetPlayerAPIMessage.findPlayer(connection, playerId)
      .getOrElse(throw NoSuchElementException("Resource not found"))

  private def playerProfileView(player: Player): PlayerProfileView =
    PlayerProfileView(
      playerId = player.id.value,
      userId = player.userId,
      nickname = player.nickname,
      registeredAt = player.registeredAt.toString,
      currentRank = RankSnapshotView(player.currentRank.platform, player.currentRank.tier, player.currentRank.stars),
      elo = player.elo,
      clubId = player.clubId.map(_.value),
      affiliatedClubIds = player.affiliatedClubIds.map(_.value),
      status = player.status.toString,
      roles = PlayerRoleFlagsView(
        isRegisteredPlayer = player.effectiveRoleGrants.exists(_.role == Role.RegisteredPlayer),
        isClubAdmin = player.roleGrants.exists(_.role == Role.ClubAdmin),
        isTournamentAdmin = player.roleGrants.exists(_.role == Role.TournamentAdmin),
        isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
      ),
      bannedReason = player.bannedReason
    )

object GetPlayerAPIMessage:
  def findPlayer(connection: Connection, playerId: PlayerId): Option[Player] =
    PlayerTable.findById(connection, playerId)
