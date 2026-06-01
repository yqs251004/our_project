package riichinexus.microservices.player.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.PlayerId
import riichinexus.microservices.auth.domain.model.Role
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.objects.apiTypes.{PlayerProfileView, PlayerRoleFlagsView}
import riichinexus.microservices.player.tables.players.PlayerTable
import upickle.default.*

final case class GetCurrentPlayerAPIMessage(
    operatorId: String
) extends APIMessage[PlayerProfileView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PlayerProfileView] =
    for
      playerId <- IO.blocking(resolvePlayerId)
      player <- IO.blocking(findPlayer(context.connection, playerId))
    yield playerProfileView(player)

  private def resolvePlayerId: PlayerId =
    Option(operatorId).map(_.trim).filter(_.nonEmpty)
      .map(PlayerId(_))
      .getOrElse(throw IllegalArgumentException("Input field operatorId is required"))

  private def findPlayer(connection: java.sql.Connection, playerId: PlayerId) =
    PlayerTable.findById(connection, playerId)
      .getOrElse(throw NoSuchElementException("Resource not found"))

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
