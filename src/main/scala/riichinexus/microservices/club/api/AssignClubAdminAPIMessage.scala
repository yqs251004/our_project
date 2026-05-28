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
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.ClubView
import riichinexus.microservices.player.tables.player.PlayerTable
import upickle.default.*

final case class AssignClubAdminAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: String
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(context.principal(PlayerId(operatorId)))
      grantedAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = AssignClubAdminCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        grantedAt = grantedAt
      )
      club <- IO.blocking {
        module.transactionManager.inTransaction {
          assignAdmin(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubView.fromDomain(club)

  private def assignAdmin(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: AssignClubAdminCommand
  ): Option[Club] =
    for
      club <- riichinexus.microservices.club.tables.club.ClubTable.findById(connection, command.clubId)
      player <- PlayerTable.findById(connection, command.playerId)
    yield
      ensureAdminCanBeAssigned(module, club, player, command)
      PlayerTable.save(
        connection,
        player.grantRole(RoleGrant.clubAdmin(command.clubId, command.grantedAt, command.actor.playerId))
      )
      riichinexus.microservices.club.tables.club.ClubTable.save(connection, club.grantAdmin(command.playerId))

  private def ensureAdminCanBeAssigned(
      module: ClubModuleContext,
      club: Club,
      player: Player,
      command: AssignClubAdminCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    requireActivePlayer(player, s"Player ${command.playerId.value} cannot be granted club admin")
    ClubAuthorization.requireClubMember(club, command.playerId, "assign club admin")
    ClubAuthorization.requireClubAdmin(
      module = module,
      actor = command.actor,
      club = club,
      permission = Permission.AssignClubAdmin
    )

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class AssignClubAdminCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant
  )
