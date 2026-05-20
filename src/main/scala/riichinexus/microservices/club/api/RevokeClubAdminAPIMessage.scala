package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class RevokeClubAdminAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: Option[String] = None
) extends APIMessage[Club] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Club] =
    IO {
      val module = context.support.clubModule
      val parsedClubId = ClubId(clubId)
      val parsedPlayerId = PlayerId(playerId)
      val actor = operatorId.filter(_.nonEmpty).map(id => context.support.principal(PlayerId(id))).getOrElse(AccessPrincipal.system)

      module.transactionManager.inTransaction {
        (for
          club <- module.clubRepository.findById(parsedClubId)
          player <- module.playerRepository.findById(parsedPlayerId)
        yield
          ensureClubActive(club)
          requireClubMember(club, parsedPlayerId, "revoke club admin")
          module.authorizationService.requirePermission(
            actor,
            Permission.AssignClubAdmin,
            clubId = Some(parsedClubId)
          )

          if !club.admins.contains(parsedPlayerId) then
            throw IllegalArgumentException(
              s"Player ${parsedPlayerId.value} is not a club admin of club ${parsedClubId.value}"
            )

          if club.admins.size <= 1 then
            throw IllegalArgumentException(
              s"Club ${parsedClubId.value} must retain at least one club admin"
            )

          module.playerRepository.save(player.revokeClubAdmin(parsedClubId))
          module.clubRepository.save(club.revokeAdmin(parsedPlayerId))
        ).getOrElse(throw NoSuchElementException("Resource not found"))
      }
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireClubMember(club: Club, playerId: PlayerId, action: String): Unit =
    if !club.members.contains(playerId) then
      throw IllegalArgumentException(
        s"Player ${playerId.value} must be a club member to $action in club ${club.id.value}"
      )
