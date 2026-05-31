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

final case class AdjustClubPointPoolAPIMessage(
    clubId: String,
    operatorId: String,
    delta: Int,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(context.principal(PlayerId(operatorId)))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = AdjustClubPointPoolCommand(
        clubId = ClubId(clubId),
        actor = actor,
        delta = delta,
        note = note,
        occurredAt = occurredAt
      )
      club <- IO.blocking {
        module.transactionManager.inTransaction {
          adjustPointPool(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubView.fromDomain(club)

  private def adjustPointPool(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: AdjustClubPointPoolCommand
  ): Option[Club] =
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubCapability(
        module = module,
        actor = command.actor,
        club = club,
        permission = Permission.ManageClubOperations,
        delegatedPrivileges = Set(ClubPrivilege.ManageBank)
      )
      commitPointPoolAdjustment(connection, module, club, command)
    }

  private def commitPointPoolAdjustment(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      club: Club,
      command: AdjustClubPointPoolCommand
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.adjustPointPool(command.delta),
        persist = updatedClub => riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, updatedClub),
        aggregateType = "club",
        aggregateId = _.id.value,
        eventType = "ClubPointPoolAdjusted",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = updatedClub =>
          Map(
            "delta" -> command.delta.toString,
            "pointPool" -> updatedClub.pointPool.toString
          ),
        note = command.note
      )

  private final case class AdjustClubPointPoolCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      delta: Int,
      note: Option[String],
      occurredAt: Instant
  )
