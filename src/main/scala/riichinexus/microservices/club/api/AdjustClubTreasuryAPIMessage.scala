package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.DomainChangeInterpreter
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.ClubView
import upickle.default.*

final case class AdjustClubTreasuryAPIMessage(
    clubId: String,
    operatorId: String,
    delta: Long,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO(context.principal(PlayerId(operatorId)))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = AdjustClubTreasuryCommand(
        clubId = ClubId(clubId),
        actor = actor,
        delta = delta,
        note = note,
        occurredAt = occurredAt
      )
      club <- IO {
        module.transactionManager.inTransaction {
          adjustTreasury(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubView.fromDomain(club)

  private def adjustTreasury(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: AdjustClubTreasuryCommand
  ): Option[Club] =
    riichinexus.microservices.club.tables.club.ClubTable.findById(connection, command.clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubCapability(
        module = module,
        actor = command.actor,
        club = club,
        permission = Permission.ManageClubOperations,
        delegatedPrivileges = Set(ClubPrivilege.ManageBank)
      )
      commitTreasuryAdjustment(connection, module, club, command)
    }

  private def commitTreasuryAdjustment(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      club: Club,
      command: AdjustClubTreasuryCommand
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.adjustTreasury(command.delta),
        persist = updatedClub => riichinexus.microservices.club.tables.club.ClubTable.save(connection, updatedClub),
        aggregateType = "club",
        aggregateId = _.id.value,
        eventType = "ClubTreasuryAdjusted",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = updatedClub =>
          Map(
            "delta" -> command.delta.toString,
            "treasuryBalance" -> updatedClub.treasuryBalance.toString
          ),
        note = command.note
      )

  private final case class AdjustClubTreasuryCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      delta: Long,
      note: Option[String],
      occurredAt: Instant
  )
