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
import riichinexus.microservices.club.domain.ClubProjectionRefresher
import riichinexus.microservices.club.objects.{Club as ClubResponse}
import riichinexus.microservices.player.tables.player.PlayerTable
import upickle.default.*

final case class RemoveClubMemberAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      actor <- IO(resolveOperatorActor(context))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = RemoveClubMemberCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        occurredAt = occurredAt
      )
      club <- IO {
        module.transactionManager
          .inTransaction {
            removeClubMember(context.connection, module, command)
          }
          .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubResponse.fromDomain(club)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    operatorId.filter(_.nonEmpty)
      .map(id => context.principal(PlayerId(id)))
      .getOrElse(AccessPrincipal.system)

  private def removeClubMember(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: RemoveClubMemberCommand
  ): Option[Club] =
    for
      club <- module.clubRepository.findById(command.clubId)
      player <- PlayerTable.findById(connection, command.playerId)
    yield
      ensureClubActive(club)
      requireClubMember(club, command.playerId, "remove member")
      requireClubCapability(
        module = module,
        actor = command.actor,
        club = club,
        permission = Permission.ManageClubMembership,
        delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
      )
      ensureMemberCanBeRemoved(club, command.clubId, command.playerId)

      PlayerTable.save(
        connection,
        player
          .leaveClub(command.clubId)
          .revokeClubAdmin(command.clubId)
      )
      module.clubRepository.save(
        ClubProjectionRefresher.refreshClubProjection(connection, module, club.removeMember(command.playerId), command.occurredAt)
      )

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireClubMember(club: Club, playerId: PlayerId, action: String): Unit =
    if !club.members.contains(playerId) then
      throw IllegalArgumentException(
        s"Player ${playerId.value} must be a club member to $action in club ${club.id.value}"
      )

  private def requireClubCapability(
      module: ClubModuleContext,
      actor: AccessPrincipal,
      club: Club,
      permission: Permission,
      delegatedPrivileges: Set[String]
  ): Unit =
    val authorizationService = module.authorizationService
    val hasBasePermission = authorizationService.can(actor, permission, clubId = Some(club.id))
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) &&
        delegatedPrivileges.exists(privilege => club.hasPrivilege(playerId, privilege))
    }

    if !hasBasePermission && !hasDelegatedPrivilege then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform $permission in club ${club.id.value}"
      )

  private def ensureMemberCanBeRemoved(club: Club, clubId: ClubId, playerId: PlayerId): Unit =
    if club.creator == playerId then
      throw IllegalArgumentException(
        s"Club creator ${playerId.value} cannot be removed from active club ${clubId.value}"
      )

    if club.admins.contains(playerId) && club.admins.size <= 1 then
      throw IllegalArgumentException(
        s"Club ${clubId.value} must retain at least one club admin before removing ${playerId.value}"
      )

  private final case class RemoveClubMemberCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      occurredAt: Instant
  )
