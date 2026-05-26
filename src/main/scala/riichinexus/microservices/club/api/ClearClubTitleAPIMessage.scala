package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.DomainChangeInterpreter
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.ClubView
import riichinexus.microservices.player.tables.player.PlayerTable
import upickle.default.*

final case class ClearClubTitleAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: String,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO(context.principal(PlayerId(operatorId)))
      clearedAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = ClearClubTitleCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        note = note,
        clearedAt = clearedAt
      )
      club <- IO {
        module.transactionManager.inTransaction {
          clearTitle(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubView.fromDomain(club)

  private def clearTitle(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: ClearClubTitleCommand
  ): Option[Club] =
    for
      club <- riichinexus.microservices.club.tables.club.ClubTable.findById(connection, command.clubId)
      player <- PlayerTable.findById(connection, command.playerId)
    yield
      ensureTitleCanBeCleared(module, club, player, command)
      val existingAssignment = resolveExistingAssignment(club, command)
      commitTitleClear(connection, module, club, command, existingAssignment)

  private def ensureTitleCanBeCleared(
      module: ClubModuleContext,
      club: Club,
      player: Player,
      command: ClearClubTitleCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    requireActivePlayer(player, s"Player ${command.playerId.value} cannot clear club title")
    ClubAuthorization.requireClubMember(club, command.playerId, "clear internal title")
    ClubAuthorization.requireClubAdmin(
      module = module,
      actor = command.actor,
      club = club,
      permission = Permission.SetClubTitle
    )

  private def resolveExistingAssignment(
      club: Club,
      command: ClearClubTitleCommand
  ): ClubTitleAssignment =
    club.titleAssignments.find(_.playerId == command.playerId)
      .getOrElse(
        throw NoSuchElementException(
          s"Player ${command.playerId.value} does not hold a title in club ${command.clubId.value}"
        )
      )

  private def commitTitleClear(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      club: Club,
      command: ClearClubTitleCommand,
      existingAssignment: ClubTitleAssignment
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.clearInternalTitle(command.playerId),
        persist = updatedClub => riichinexus.microservices.club.tables.club.ClubTable.save(connection, updatedClub),
        aggregateType = "club",
        aggregateId = _.id.value,
        eventType = "ClubTitleCleared",
        occurredAt = command.clearedAt,
        actorId = command.actor.playerId,
        details = _ =>
          Map(
            "playerId" -> command.playerId.value,
            "title" -> existingAssignment.title
          ),
        note = command.note
      )

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class ClearClubTitleCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      note: Option[String],
      clearedAt: Instant
  )
