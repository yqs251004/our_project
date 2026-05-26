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
import riichinexus.microservices.club.objects.{Club as ClubResponse}
import riichinexus.microservices.club.objects.apiTypes.ClubRankNodeRequest
import upickle.default.*

final case class UpdateClubRankTreeAPIMessage(
    clubId: String,
    operatorId: String,
    ranks: Vector[ClubRankNodeRequest],
    note: Option[String] = None
) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      actor <- IO(context.principal(PlayerId(operatorId)))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = UpdateClubRankTreeCommand(
        clubId = ClubId(clubId),
        actor = actor,
        ranks = ranks.map(_.toNode),
        note = note,
        occurredAt = occurredAt
      )
      club <- IO {
        module.transactionManager.inTransaction {
          updateRankTree(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubResponse.fromDomain(club)

  private def updateRankTree(
      module: ClubModuleContext,
      command: UpdateClubRankTreeCommand
  ): Option[Club] =
    module.clubRepository.findById(command.clubId).map { club =>
      ensureClubActive(club)
      module.authorizationService.requirePermission(
        command.actor,
        Permission.ManageClubOperations,
        clubId = Some(command.clubId)
      )
      commitRankTreeUpdate(module, club, command)
    }

  private def commitRankTreeUpdate(
      module: ClubModuleContext,
      club: Club,
      command: UpdateClubRankTreeCommand
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.updateRankTree(command.ranks),
        persist = module.clubRepository.save,
        aggregateType = "club",
        aggregateId = _.id.value,
        eventType = "ClubRankTreeUpdated",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = updatedClub => Map("rankCount" -> updatedClub.rankTree.size.toString),
        note = command.note
      )

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private final case class UpdateClubRankTreeCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      ranks: Vector[ClubRankNode],
      note: Option[String],
      occurredAt: Instant
  )
