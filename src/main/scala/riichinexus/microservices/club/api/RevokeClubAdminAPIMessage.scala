package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.apiTypes.{Club as ClubResponse}
import upickle.default.*

final case class RevokeClubAdminAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      actor <- IO(resolveOperatorActor(context))
      module = context.support.clubModule
      command = RevokeClubAdminCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor
      )
      club <- IO {
        module.transactionManager.inTransaction {
          revokeAdmin(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubResponse.fromDomain(club)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    operatorId.filter(_.nonEmpty)
      .map(id => context.support.principal(PlayerId(id)))
      .getOrElse(AccessPrincipal.system)

  private def revokeAdmin(
      module: ClubModuleContext,
      command: RevokeClubAdminCommand
  ): Option[Club] =
    for
      club <- module.clubRepository.findById(command.clubId)
      player <- module.playerRepository.findById(command.playerId)
    yield
      ensureAdminCanBeRevoked(module, club, command)
      module.playerRepository.save(player.revokeClubAdmin(command.clubId))
      module.clubRepository.save(club.revokeAdmin(command.playerId))

  private def ensureAdminCanBeRevoked(
      module: ClubModuleContext,
      club: Club,
      command: RevokeClubAdminCommand
  ): Unit =
    ensureClubActive(club)
    requireClubMember(club, command.playerId, "revoke club admin")
    module.authorizationService.requirePermission(
      command.actor,
      Permission.AssignClubAdmin,
      clubId = Some(command.clubId)
    )
    ensureTargetIsAdmin(club, command)
    ensureAnotherAdminRemains(club, command)

  private def ensureTargetIsAdmin(club: Club, command: RevokeClubAdminCommand): Unit =
    if !club.admins.contains(command.playerId) then
      throw IllegalArgumentException(
        s"Player ${command.playerId.value} is not a club admin of club ${command.clubId.value}"
      )

  private def ensureAnotherAdminRemains(club: Club, command: RevokeClubAdminCommand): Unit =
    if club.admins.size <= 1 then
      throw IllegalArgumentException(
        s"Club ${command.clubId.value} must retain at least one club admin"
      )

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireClubMember(club: Club, playerId: PlayerId, action: String): Unit =
    if !club.members.contains(playerId) then
      throw IllegalArgumentException(
        s"Player ${playerId.value} must be a club member to $action in club ${club.id.value}"
      )

  private final case class RevokeClubAdminCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal
  )
