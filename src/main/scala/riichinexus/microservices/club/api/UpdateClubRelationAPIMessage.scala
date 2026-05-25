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

final case class UpdateClubRelationAPIMessage(
    clubId: String,
    operatorId: String,
    targetClubId: String,
    relation: String,
    note: Option[String] = None
) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      actor <- IO(context.support.principal(PlayerId(operatorId)))
      relationUpdatedAt <- IO.realTimeInstant
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = UpdateClubRelationCommand(
        clubId = ClubId(clubId),
        actor = actor,
        relation = ClubRelation(
          targetClubId = ClubId(targetClubId),
          relation = ClubRelationKind.valueOf(relation),
          updatedAt = relationUpdatedAt,
          note = note
        ),
        occurredAt = occurredAt
      )
      club <- IO {
        module.transactionManager.inTransaction {
          updateRelation(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubResponse.fromDomain(club)

  private def updateRelation(
      module: ClubModuleContext,
      command: UpdateClubRelationCommand
  ): Option[Club] =
    module.clubRepository.findById(command.clubId).map { club =>
      ensureRelationCanBeUpdated(module, club, command)
      val targetClub = resolveTargetClub(module, command)
      commitRelationUpdate(module, club, targetClub, command)
    }

  private def ensureRelationCanBeUpdated(
      module: ClubModuleContext,
      club: Club,
      command: UpdateClubRelationCommand
  ): Unit =
    ensureClubActive(club)
    module.authorizationService.requirePermission(
      command.actor,
      Permission.SetClubTitle,
      clubId = Some(command.clubId)
    )
    if command.relation.targetClubId == command.clubId then
      throw IllegalArgumentException("A club cannot define a relation to itself")

  private def resolveTargetClub(
      module: ClubModuleContext,
      command: UpdateClubRelationCommand
  ): Club =
    module.clubRepository
      .findById(command.relation.targetClubId)
      .map { club =>
        ensureClubActive(club)
        club
      }
      .getOrElse(
        throw NoSuchElementException(s"Club ${command.relation.targetClubId.value} was not found")
      )

  private def commitRelationUpdate(
      module: ClubModuleContext,
      club: Club,
      targetClub: Club,
      command: UpdateClubRelationCommand
  ): Club =
    val sourceClub =
      if command.relation.relation == ClubRelationKind.Neutral then
        club.removeRelation(command.relation.targetClubId)
      else club.upsertRelation(command.relation)

    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = sourceClub,
        persist = source =>
          val savedSource = module.clubRepository.save(source)
          if command.relation.relation == ClubRelationKind.Neutral then
            module.clubRepository.save(targetClub.removeRelation(command.clubId))
          else
            module.clubRepository.save(
              targetClub.upsertRelation(
                command.relation.copy(targetClubId = command.clubId)
              )
            )
          savedSource,
        aggregateType = "club",
        aggregateId = _.id.value,
        eventType = "ClubRelationUpdated",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = _ =>
          Map(
            "targetClubId" -> command.relation.targetClubId.value,
            "relation" -> command.relation.relation.toString
          ),
        note = command.relation.note
      )

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private final case class UpdateClubRelationCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      relation: ClubRelation,
      occurredAt: Instant
  )
