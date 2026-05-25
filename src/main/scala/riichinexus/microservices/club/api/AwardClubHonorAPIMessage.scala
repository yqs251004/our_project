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

final case class AwardClubHonorAPIMessage(
    clubId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None,
    achievedAt: Option[Instant] = None
) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      actor <- IO(context.support.principal(PlayerId(operatorId)))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = AwardClubHonorCommand(
        clubId = ClubId(clubId),
        actor = actor,
        honor = ClubHonor(title = title, achievedAt = achievedAt.getOrElse(occurredAt), note = note),
        occurredAt = occurredAt
      )
      club <- IO {
        module.transactionManager.inTransaction {
          awardHonor(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubResponse.fromDomain(club)

  private def awardHonor(
      module: ClubModuleContext,
      command: AwardClubHonorCommand
  ): Option[Club] =
    module.clubRepository.findById(command.clubId).map { club =>
      ensureClubActive(club)
      module.authorizationService.requirePermission(
        command.actor,
        Permission.ManageClubOperations,
        clubId = Some(command.clubId)
      )
      commitHonorAward(module, club, command)
    }

  private def commitHonorAward(
      module: ClubModuleContext,
      club: Club,
      command: AwardClubHonorCommand
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.addHonor(command.honor),
        persist = module.clubRepository.save,
        aggregateType = "club",
        aggregateId = _.id.value,
        eventType = "ClubHonorAwarded",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = _ => Map("title" -> command.honor.title),
        note = command.honor.note
      )

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private final case class AwardClubHonorCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      honor: ClubHonor,
      occurredAt: Instant
  )
