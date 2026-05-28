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
import riichinexus.microservices.auth.domain.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.ClubView
import upickle.default.*

final case class RevokeClubHonorAPIMessage(
    clubId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(context.principal(PlayerId(operatorId)))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = RevokeClubHonorCommand(
        clubId = ClubId(clubId),
        actor = actor,
        title = title,
        note = note,
        occurredAt = occurredAt
      )
      club <- IO.blocking {
        module.transactionManager.inTransaction {
          revokeHonor(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubView.fromDomain(club)

  private def revokeHonor(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: RevokeClubHonorCommand
  ): Option[Club] =
    riichinexus.microservices.club.tables.club.ClubTable.findById(connection, command.clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubAdmin(
        module = module,
        actor = command.actor,
        club = club,
        permission = Permission.ManageClubOperations
      )
      ensureHonorExists(club, command)
      commitHonorRevocation(connection, module, club, command)
    }

  private def ensureHonorExists(club: Club, command: RevokeClubHonorCommand): Unit =
    val normalizedTitle = command.title.trim.toLowerCase
    if !club.honors.exists(_.title.trim.toLowerCase == normalizedTitle) then
      throw NoSuchElementException(s"Club ${command.clubId.value} does not have honor '${command.title}'")

  private def commitHonorRevocation(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      club: Club,
      command: RevokeClubHonorCommand
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.removeHonor(command.title),
        persist = updatedClub => riichinexus.microservices.club.tables.club.ClubTable.save(connection, updatedClub),
        aggregateType = "club",
        aggregateId = _.id.value,
        eventType = "ClubHonorRevoked",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = _ => Map("title" -> command.title),
        note = command.note
      )

  private final case class RevokeClubHonorCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      title: String,
      note: Option[String],
      occurredAt: Instant
  )
