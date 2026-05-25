package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.DomainChangeInterpreter
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.apiTypes.{Club as ClubResponse}
import upickle.default.*

final case class ClearClubTitleAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: String,
    note: Option[String] = None
) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      actor <- IO(context.support.principal(PlayerId(operatorId)))
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
          clearTitle(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubResponse.fromDomain(club)

  private def clearTitle(
      module: ClubModuleContext,
      command: ClearClubTitleCommand
  ): Option[Club] =
    for
      club <- module.clubRepository.findById(command.clubId)
      player <- module.playerRepository.findById(command.playerId)
    yield
      ensureTitleCanBeCleared(module, club, player, command)
      val existingAssignment = resolveExistingAssignment(club, command)
      commitTitleClear(module, club, command, existingAssignment)

  private def ensureTitleCanBeCleared(
      module: ClubModuleContext,
      club: Club,
      player: Player,
      command: ClearClubTitleCommand
  ): Unit =
    ensureClubActive(club)
    requireActivePlayer(player, s"Player ${command.playerId.value} cannot clear club title")
    requireClubMember(club, command.playerId, "clear internal title")
    module.authorizationService.requirePermission(
      command.actor,
      Permission.SetClubTitle,
      clubId = Some(command.clubId)
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
      module: ClubModuleContext,
      club: Club,
      command: ClearClubTitleCommand,
      existingAssignment: ClubTitleAssignment
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.clearInternalTitle(command.playerId),
        persist = module.clubRepository.save,
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

  private final case class ClearClubTitleCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      note: Option[String],
      clearedAt: Instant
  )
