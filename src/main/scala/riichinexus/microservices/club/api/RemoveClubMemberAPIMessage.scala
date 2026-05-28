package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.auth.domain.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.{ClubAuthorization, ClubProjectionRefresher}
import riichinexus.microservices.club.objects.ClubView
import riichinexus.microservices.player.tables.player.PlayerTable
import upickle.default.*

final case class RemoveClubMemberAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(resolveOperatorActor(context))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = RemoveClubMemberCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        occurredAt = occurredAt
      )
      club <- IO.blocking {
        module.transactionManager
          .inTransaction {
            removeClubMember(context.connection, module, command)
          }
          .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubView.fromDomain(club)

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
      club <- riichinexus.microservices.club.tables.club.ClubTable.findById(connection, command.clubId)
      player <- PlayerTable.findById(connection, command.playerId)
    yield
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubMember(club, command.playerId, "remove member")
      ClubAuthorization.requireClubCapability(
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
      riichinexus.microservices.club.tables.club.ClubTable.save(connection, 
        ClubProjectionRefresher.refreshClubProjection(connection, module, club.removeMember(command.playerId), command.occurredAt)
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
