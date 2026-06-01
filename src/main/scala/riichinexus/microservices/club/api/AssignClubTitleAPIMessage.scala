package riichinexus.microservices.club.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.DomainChangeInterpreter
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
import riichinexus.microservices.auth.domain.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class AssignClubTitleAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(AuthAccessPrincipalResolver.principal(context, PlayerId(operatorId)))
      assignedAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = AssignClubTitleCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        title = title,
        note = note,
        assignedAt = assignedAt
      )
      club <- IO.blocking {
        module.transactionManager.inTransaction {
          assignTitle(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubView.fromDomain(club)

  private def assignTitle(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: AssignClubTitleCommand
  ): Option[Club] =
    for
      club <- riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId)
      player <- GetPlayerAPIMessage.findPlayer(connection, command.playerId)
    yield
      ensureTitleCanBeAssigned(module, club, player, command)
      commitTitleAssignment(connection, module, club, command, assignedBy = command.actor.playerId.getOrElse(club.creator))

  private def ensureTitleCanBeAssigned(
      module: ClubModuleContext,
      club: Club,
      player: Player,
      command: AssignClubTitleCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    requireActivePlayer(player, s"Player ${command.playerId.value} cannot receive club title")
    ClubAuthorization.requireClubMember(club, command.playerId, "set internal title")
    ClubAuthorization.requireClubAdmin(
      module = module,
      actor = command.actor,
      club = club,
      permission = Permission.SetClubTitle
    )

  private def commitTitleAssignment(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      club: Club,
      command: AssignClubTitleCommand,
      assignedBy: PlayerId
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = ClubFunctions.setInternalTitle(club,
          ClubTitleAssignment(
            playerId = command.playerId,
            title = command.title,
            assignedBy = assignedBy,
            assignedAt = command.assignedAt,
            note = command.note
          )
        ),
        persist = updatedClub => riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, updatedClub),
        aggregateType = "club",
        aggregateId = _.id.value,
        eventType = "ClubTitleAssigned",
        occurredAt = command.assignedAt,
        actorId = command.actor.playerId,
        details = _ =>
          Map(
            "playerId" -> command.playerId.value,
            "title" -> command.title
          ),
        note = command.note
      )

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class AssignClubTitleCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      title: String,
      note: Option[String],
      assignedAt: Instant
  )
