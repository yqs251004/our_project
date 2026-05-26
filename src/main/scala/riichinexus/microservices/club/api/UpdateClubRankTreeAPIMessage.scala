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
import riichinexus.microservices.club.objects.apiTypes.ClubRankNodeRequest
import upickle.default.*

final case class UpdateClubRankTreeAPIMessage(
    clubId: String,
    operatorId: String,
    ranks: Vector[ClubRankNodeRequest],
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
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
          updateRankTree(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubView.fromDomain(club)

  private def updateRankTree(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: UpdateClubRankTreeCommand
  ): Option[Club] =
    riichinexus.microservices.club.tables.club.ClubTable.findById(connection, command.clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      ClubAuthorization.requireClubAdmin(
        module = module,
        actor = command.actor,
        club = club,
        permission = Permission.ManageClubOperations
      )
      commitRankTreeUpdate(connection, module, club, command)
    }

  private def commitRankTreeUpdate(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      club: Club,
      command: UpdateClubRankTreeCommand
  ): Club =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = club.updateRankTree(command.ranks),
        persist = updatedClub => riichinexus.microservices.club.tables.club.ClubTable.save(connection, updatedClub),
        aggregateType = "club",
        aggregateId = _.id.value,
        eventType = "ClubRankTreeUpdated",
        occurredAt = command.occurredAt,
        actorId = command.actor.playerId,
        details = updatedClub => Map("rankCount" -> updatedClub.rankTree.size.toString),
        note = command.note
      )

  private final case class UpdateClubRankTreeCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      ranks: Vector[ClubRankNode],
      note: Option[String],
      occurredAt: Instant
  )
