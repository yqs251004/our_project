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

final case class AwardClubHonorAPIMessage(
    clubId: String,
    operatorId: String,
    title: String,
    note: Option[String] = None,
    achievedAt: Option[Instant] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(context.principal(PlayerId(operatorId)))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = AwardClubHonorCommand(
        clubId = ClubId(clubId),
        actor = actor,
        honor = ClubHonor(title = title, achievedAt = achievedAt.getOrElse(occurredAt), note = note),
        occurredAt = occurredAt
      )
      club <- IO.blocking {
        module.transactionManager.inTransaction {
          awardHonor(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubView.fromDomain(club)

  private def awardHonor(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: AwardClubHonorCommand
  ): Option[Club] =
    riichinexus.microservices.club.tables.club.ClubTable.findById(connection, command.clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubAdmin(
        module = module,
        actor = command.actor,
        club = club,
        permission = Permission.ManageClubOperations
      )
      commitHonorAward(connection, module, club, command)
    }

  private def commitHonorAward(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      club: Club,
      command: AwardClubHonorCommand
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.addHonor(command.honor),
        persist = updatedClub => riichinexus.microservices.club.tables.club.ClubTable.save(connection, updatedClub),
        aggregateType = "club",
        aggregateId = _.id.value,
        eventType = "ClubHonorAwarded",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = _ => Map("title" -> command.honor.title),
        note = command.honor.note
      )

  private final case class AwardClubHonorCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      honor: ClubHonor,
      occurredAt: Instant
  )
