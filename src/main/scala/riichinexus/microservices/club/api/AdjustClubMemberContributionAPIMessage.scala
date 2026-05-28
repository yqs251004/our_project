package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.DomainChangeInterpreter
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

final case class AdjustClubMemberContributionAPIMessage(
    clubId: String,
    operatorId: String,
    playerId: String,
    delta: Int,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(context.principal(PlayerId(operatorId)))
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
      club <- IO.blocking {
        module.transactionManager.inTransaction {
          adjustMemberContribution(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubView.fromDomain(club)

  private def adjustMemberContribution(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: AdjustClubMemberContributionCommand
  ): Option[Club] =
    for
      club <- riichinexus.microservices.club.tables.club.ClubTable.findById(connection, command.clubId)
      player <- PlayerTable.findById(connection, command.playerId)
    yield
      ensureContributionCanBeAdjusted(module, club, player, command)
      val nextContribution = resolveNextContribution(club, command)
      val updatedBy = command.actor.playerId.getOrElse(club.creator)
      commitContributionAdjustment(connection, module, club, command, nextContribution, updatedBy)

  private def ensureContributionCanBeAdjusted(
      module: ClubModuleContext,
      club: Club,
      player: Player,
      command: AdjustClubMemberContributionCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    requireActivePlayer(player, s"Player ${command.playerId.value} cannot receive club contribution updates")
    ClubAuthorization.requireClubMember(club, command.playerId, "adjust contribution")
    ClubAuthorization.requireClubAdmin(
      module = module,
      actor = command.actor,
      club = club,
      permission = Permission.ManageClubOperations
    )

  private def resolveNextContribution(
      club: Club,
      command: AdjustClubMemberContributionCommand
  ): Int =
    val nextContribution = club.contributionOf(command.playerId) + command.delta
    require(nextContribution >= 0, s"Club member contribution for ${command.playerId.value} cannot be negative")
    nextContribution

  private def commitContributionAdjustment(
      connection: java.sql.Connection,
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
        persist = updatedClub => riichinexus.microservices.club.tables.club.ClubTable.save(connection, updatedClub),
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

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class AdjustClubMemberContributionCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      delta: Int,
      note: Option[String],
      occurredAt: Instant
  )
