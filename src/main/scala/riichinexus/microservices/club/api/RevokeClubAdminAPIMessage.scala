package riichinexus.microservices.club.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.auth.domain.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class RevokeClubAdminAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(resolveOperatorActor(context))
      module = context.support.clubModule
      command = RevokeClubAdminCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor
      )
      club <- IO.blocking {
        module.transactionManager.inTransaction {
          revokeAdmin(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubView.fromDomain(club)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    operatorId.filter(_.nonEmpty)
      .map(id => AuthAccessPrincipalResolver.principal(context, PlayerId(id)))
      .getOrElse(AccessPrincipalFunctions.system)

  private def revokeAdmin(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: RevokeClubAdminCommand
  ): Option[Club] =
    for
      club <- riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId)
      player <- GetPlayerAPIMessage.findPlayer(connection, command.playerId)
    yield
      ensureAdminCanBeRevoked(module, club, command)
      CreatePlayerAPIMessage.persistPlayer(connection, PlayerRoleFunctions.revokeClubAdmin(player, command.clubId))
      riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.revokeAdmin(club, command.playerId))

  private def ensureAdminCanBeRevoked(
      module: ClubModuleContext,
      club: Club,
      command: RevokeClubAdminCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    ClubAuthorization.requireClubMember(club, command.playerId, "revoke club admin")
    ClubAuthorization.requireClubAdmin(
      module = module,
      actor = command.actor,
      club = club,
      permission = Permission.AssignClubAdmin
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

  private final case class RevokeClubAdminCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal
  )
