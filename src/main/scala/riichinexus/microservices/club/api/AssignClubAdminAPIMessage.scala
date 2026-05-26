package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.{Club as ClubResponse}
import riichinexus.microservices.player.tables.player.PlayerTable
import upickle.default.*

final case class AssignClubAdminAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: String
) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      actor <- IO(context.principal(PlayerId(operatorId)))
      grantedAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = AssignClubAdminCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        grantedAt = grantedAt
      )
      club <- IO {
        module.transactionManager.inTransaction {
          assignAdmin(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubResponse.fromDomain(club)

  private def assignAdmin(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: AssignClubAdminCommand
  ): Option[Club] =
    for
      club <- module.clubRepository.findById(command.clubId)
      player <- PlayerTable.findById(connection, command.playerId)
    yield
      ensureAdminCanBeAssigned(module, club, player, command)
      PlayerTable.save(
        connection,
        player.grantRole(RoleGrant.clubAdmin(command.clubId, command.grantedAt, command.actor.playerId))
      )
      module.clubRepository.save(club.grantAdmin(command.playerId))

  private def ensureAdminCanBeAssigned(
      module: ClubModuleContext,
      club: Club,
      player: Player,
      command: AssignClubAdminCommand
  ): Unit =
    ensureClubActive(club)
    requireActivePlayer(player, s"Player ${command.playerId.value} cannot be granted club admin")
    requireClubMember(club, command.playerId, "assign club admin")
    module.authorizationService.requirePermission(
      command.actor,
      Permission.AssignClubAdmin,
      clubId = Some(command.clubId)
    )

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private def requireClubMember(club: Club, playerId: PlayerId, action: String): Unit =
    if !club.members.contains(playerId) then
      throw IllegalArgumentException(
        s"Player ${playerId.value} must be a club member to $action in club ${club.id.value}"
      )

  private final case class AssignClubAdminCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant
  )
