package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.DomainChangeInterpreter
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.{Club as ClubResponse}
import riichinexus.microservices.player.tables.player.PlayerTable
import upickle.default.*

final case class AssignClubTitleAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None
) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      actor <- IO(context.principal(PlayerId(operatorId)))
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
      club <- IO {
        module.transactionManager.inTransaction {
          assignTitle(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubResponse.fromDomain(club)

  private def assignTitle(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: AssignClubTitleCommand
  ): Option[Club] =
    for
      club <- module.clubRepository.findById(command.clubId)
      player <- PlayerTable.findById(connection, command.playerId)
    yield
      ensureTitleCanBeAssigned(module, club, player, command)
      commitTitleAssignment(module, club, command, assignedBy = command.actor.playerId.getOrElse(club.creator))

  private def ensureTitleCanBeAssigned(
      module: ClubModuleContext,
      club: Club,
      player: Player,
      command: AssignClubTitleCommand
  ): Unit =
    ensureClubActive(club)
    requireActivePlayer(player, s"Player ${command.playerId.value} cannot receive club title")
    requireClubMember(club, command.playerId, "set internal title")
    module.authorizationService.requirePermission(
      command.actor,
      Permission.SetClubTitle,
      clubId = Some(command.clubId)
    )

  private def commitTitleAssignment(
      module: ClubModuleContext,
      club: Club,
      command: AssignClubTitleCommand,
      assignedBy: PlayerId
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.setInternalTitle(
          ClubTitleAssignment(
            playerId = command.playerId,
            title = command.title,
            assignedBy = assignedBy,
            assignedAt = command.assignedAt,
            note = command.note
          )
        ),
        persist = module.clubRepository.save,
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

  private final case class AssignClubTitleCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      title: String,
      note: Option[String],
      assignedAt: Instant
  )
