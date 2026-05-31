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
import riichinexus.microservices.club.objects.ClubRelationKind
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.ClubView
import upickle.default.*

final case class UpdateClubRelationAPIMessage(
    clubId: String,
    operatorId: String,
    targetClubId: String,
    relation: ClubRelationKind,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(context.principal(PlayerId(operatorId)))
      relationUpdatedAt <- IO.realTimeInstant
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = UpdateClubRelationCommand(
        clubId = ClubId(clubId),
        actor = actor,
        relation = ClubRelation(
          targetClubId = ClubId(targetClubId),
          relation = relation,
          updatedAt = relationUpdatedAt,
          note = note
        ),
        occurredAt = occurredAt
      )
      club <- IO.blocking {
        module.transactionManager.inTransaction {
          updateRelation(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubView.fromDomain(club)

  private def updateRelation(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: UpdateClubRelationCommand
  ): Option[Club] =
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId).map { club =>
      ensureRelationCanBeUpdated(module, club, command)
      val targetClub = resolveTargetClub(connection, command)
      commitRelationUpdate(connection, module, club, targetClub, command)
    }

  private def ensureRelationCanBeUpdated(
      module: ClubModuleContext,
      club: Club,
      command: UpdateClubRelationCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    ClubAuthorization.requireClubAdmin(
      module = module,
      actor = command.actor,
      club = club,
      permission = Permission.SetClubTitle
    )
    if command.relation.targetClubId == command.clubId then
      throw IllegalArgumentException("A club cannot define a relation to itself")

  private def resolveTargetClub(
      connection: java.sql.Connection,
      command: UpdateClubRelationCommand
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable
      .findById(connection, command.relation.targetClubId)
      .map { club =>
        ClubAuthorization.ensureClubActive(club)
        club
      }
      .getOrElse(
        throw NoSuchElementException(s"Club ${command.relation.targetClubId.value} was not found")
      )

  private def commitRelationUpdate(
      connection: java.sql.Connection,
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
          val savedSource = riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, source)
          if command.relation.relation == ClubRelationKind.Neutral then
            riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, targetClub.removeRelation(command.clubId))
          else
            riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, 
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

  private final case class UpdateClubRelationCommand(
      clubId: ClubId,
      actor: AccessPrincipal,
      relation: ClubRelation,
      occurredAt: Instant
  )
