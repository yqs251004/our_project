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

final case class RevokeClubHonorAPIMessage(
    clubId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None
) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      actor <- IO(context.support.principal(PlayerId(operatorId)))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = RevokeClubHonorCommand(
        clubId = ClubId(clubId),
        actor = actor,
        title = title,
        note = note,
        occurredAt = occurredAt
      )
      club <- IO {
        module.transactionManager.inTransaction {
          revokeHonor(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubResponse.fromDomain(club)

  private def revokeHonor(
      module: ClubModuleContext,
      command: RevokeClubHonorCommand
  ): Option[Club] =
    module.clubRepository.findById(command.clubId).map { club =>
      ensureClubActive(club)
      module.authorizationService.requirePermission(
        command.actor,
        Permission.ManageClubOperations,
        clubId = Some(command.clubId)
      )
      ensureHonorExists(club, command)
      commitHonorRevocation(module, club, command)
    }

  private def ensureHonorExists(club: Club, command: RevokeClubHonorCommand): Unit =
    val normalizedTitle = command.title.trim.toLowerCase
    if !club.honors.exists(_.title.trim.toLowerCase == normalizedTitle) then
      throw NoSuchElementException(s"Club ${command.clubId.value} does not have honor '${command.title}'")

  private def commitHonorRevocation(
      module: ClubModuleContext,
      club: Club,
      command: RevokeClubHonorCommand
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.removeHonor(command.title),
        persist = module.clubRepository.save,
        aggregateType = "club",
        aggregateId = _.id.value,
        eventType = "ClubHonorRevoked",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = _ => Map("title" -> command.title),
        note = command.note
      )

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private final case class RevokeClubHonorCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      title: String,
      note: Option[String],
      occurredAt: Instant
  )
