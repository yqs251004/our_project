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
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class AddClubMemberAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(resolveOperatorActor(context))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = AddClubMemberCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        occurredAt = occurredAt
      )
      club <- IO.blocking {
        module.transactionManager
          .inTransaction {
            addClubMember(context.connection, module, command)
          }
          .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubView.fromDomain(club)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    operatorId.filter(_.nonEmpty)
      .map(id => context.principal(PlayerId(id)))
      .getOrElse(AccessPrincipal.system)

  private def addClubMember(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: AddClubMemberCommand
  ): Option[Club] =
    for
      club <- riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId)
      player <- GetPlayerAPIMessage.findPlayer(connection, command.playerId)
    yield
      ClubAuthorization.ensureClubActive(club)
      requireActivePlayer(player, s"Player ${command.playerId.value} cannot join club ${command.clubId.value}")
      ClubAuthorization.requireClubCapability(
        module = module,
        actor = command.actor,
        club = club,
        permission = Permission.ManageClubMembership,
        delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
      )

      val savedPlayer = CreatePlayerAPIMessage.persistPlayer(connection, player.joinClub(command.clubId))
      ClubProjectionRefresher.ensurePlayerDashboard(connection, savedPlayer.id, command.occurredAt)
      riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, 
        ClubProjectionRefresher.refreshClubProjection(connection, module, club.addMember(command.playerId), command.occurredAt)
      )

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class AddClubMemberCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      occurredAt: Instant
  )
