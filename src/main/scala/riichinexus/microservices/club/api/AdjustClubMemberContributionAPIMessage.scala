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

final case class AdjustClubMemberContributionAPIMessage(
    clubId: String,
    operatorId: String,
    playerId: String,
    delta: Int,
    note: Option[String] = None
) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      actor <- IO(context.principal(PlayerId(operatorId)))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = AdjustClubMemberContributionCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        delta = delta,
        note = note,
        occurredAt = occurredAt
      )
      club <- IO {
        module.transactionManager.inTransaction {
          adjustMemberContribution(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubResponse.fromDomain(club)

  private def adjustMemberContribution(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: AdjustClubMemberContributionCommand
  ): Option[Club] =
    for
      club <- module.clubRepository.findById(command.clubId)
      player <- PlayerTable.findById(connection, command.playerId)
    yield
      ensureContributionCanBeAdjusted(module, club, player, command)
      val nextContribution = resolveNextContribution(club, command)
      val updatedBy = command.actor.playerId.getOrElse(club.creator)
      commitContributionAdjustment(module, club, command, nextContribution, updatedBy)

  private def ensureContributionCanBeAdjusted(
      module: ClubModuleContext,
      club: Club,
      player: Player,
      command: AdjustClubMemberContributionCommand
  ): Unit =
    ensureClubActive(club)
    requireActivePlayer(player, s"Player ${command.playerId.value} cannot receive club contribution updates")
    requireClubMember(club, command.playerId, "adjust contribution")
    module.authorizationService.requirePermission(
      command.actor,
      Permission.ManageClubOperations,
      clubId = Some(command.clubId)
    )

  private def resolveNextContribution(
      club: Club,
      command: AdjustClubMemberContributionCommand
  ): Int =
    val nextContribution = club.contributionOf(command.playerId) + command.delta
    require(nextContribution >= 0, s"Club member contribution for ${command.playerId.value} cannot be negative")
    nextContribution

  private def commitContributionAdjustment(
      module: ClubModuleContext,
      club: Club,
      command: AdjustClubMemberContributionCommand,
      nextContribution: Int,
      updatedBy: PlayerId
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.updateMemberContribution(
          ClubMemberContribution(
            playerId = command.playerId,
            amount = nextContribution,
            updatedAt = command.occurredAt,
            updatedBy = updatedBy,
            note = command.note
          )
        ),
        persist = module.clubRepository.save,
        aggregateType = "club",
        aggregateId = _.id.value,
        eventType = "ClubMemberContributionAdjusted",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = updatedClub =>
          Map(
            "playerId" -> command.playerId.value,
            "delta" -> command.delta.toString,
            "contribution" -> nextContribution.toString,
            "rankCode" -> updatedClub.rankFor(command.playerId).map(_.code).getOrElse("unknown")
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

  private final case class AdjustClubMemberContributionCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      delta: Int,
      note: Option[String],
      occurredAt: Instant
  )
